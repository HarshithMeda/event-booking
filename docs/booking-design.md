# Booking design: how double booking is prevented

This document explains how the event-booking API lets users hold, confirm and cancel seats
without ever selling the same seat twice, even when many people click "book" at the same moment.

---

## 1. Key terms

Each term has a **technical** definition first, then a **simple** explanation.

**Race condition**
- *Technical:* a bug where the result depends on the timing of two or more operations running at the same time.
- *Simple:* two people click "book seat A1" in the same millisecond. If the code isn't careful, both are told "success" and the seat is sold twice.

**Transaction**
- *Technical:* a group of database operations treated as one unit. It either *commits* (every change is saved) or *rolls back* (every change is undone).
- *Simple:* all or nothing. If you hold 3 seats and the 3rd fails, the first 2 are undone too.

**ACID (Atomicity, Consistency, Isolation, Durability)**
- *Technical:* the four guarantees a database gives a transaction:
  - **Atomicity:** all or nothing.
  - **Consistency:** rules like CHECK constraints and foreign keys always hold.
  - **Isolation:** transactions running at the same time don't see each other's half-finished work.
  - **Durability:** once committed, data survives a crash.
- *Simple:* the promises that make "all or nothing" safe.

**Isolation level: READ COMMITTED**
- *Technical:* PostgreSQL's default isolation level. Each statement sees only data that was committed before it started. When an `UPDATE` hits a row another transaction has already changed but not committed, it waits, then re-checks its `WHERE` condition against the new committed row.
- *Simple:* you never read someone's unsaved changes. If two people update the same row, the second one waits its turn and then looks again.

**Row lock**
- *Technical:* a lock the database puts on a single row during `UPDATE` (or `SELECT … FOR UPDATE`). It's released at commit or rollback.
- *Simple:* while one transaction is changing a seat, nobody else can change that same seat.

**JPA (Jakarta Persistence API) / Hibernate**
- *Technical:* JPA is the Java standard for mapping objects to database tables. Hibernate is the implementation Spring uses.
- *Simple:* the layer that turns `seat.setStatus(HELD)` into SQL.

**Optimistic locking**
- *Technical:* concurrency control with no lock taken while reading. Every row has a `version` number. An update succeeds only if the version is still the one you read:
  `UPDATE seats SET status='HELD', version=8 WHERE id=1 AND version=7`.
  If 0 rows change, someone else got there first, and Hibernate throws `OptimisticLockException`.
- *Simple:* "I assume nobody else will touch this seat, but I'll check right before saving." If the seat changed since I looked, my save fails.

**Pessimistic locking**
- *Technical:* concurrency control that locks rows at the moment you read them, using `SELECT … FOR UPDATE`. Other transactions that want those rows block until you commit or roll back.
- *Simple:* "I assume someone will fight me for this seat, so I lock it first." Everyone else waits in line.

**Deadlock**
- *Technical:* two transactions each hold a lock the other needs, so neither can continue. PostgreSQL detects this and aborts one of them.
- *Simple:* person 1 grabs seat A1 then wants A2. Person 2 grabs A2 then wants A1. Both are stuck.

**HTTP (Hypertext Transfer Protocol) 409 Conflict / 404 Not Found**
- *Technical:* 409 means the request conflicts with the current state of the resource. 404 means the resource doesn't exist, or the server won't say that it does.
- *Simple:* 409 means "that seat was just taken". 404 means "there's no such booking (for you)".

**FK (Foreign Key)**
- *Technical:* a column whose value must match the primary key of a row in another table.
- *Simple:* a booking's `user_id` must point to a real user.

---

## 2. How two people booking the same seat is handled

The app uses **optimistic locking** with the `version` column on `seats`.
Holding seats happens inside one transaction:

1. Load the requested seats, sorted by seat ID.
2. Check each one is `AVAILABLE`. If not, return 409 right away.
3. Set each one to `HELD` and flush. Hibernate runs `UPDATE … WHERE id=? AND version=?` for each seat.
4. If any update changes 0 rows, the whole transaction rolls back and the API returns 409. Nothing is held.
5. Otherwise, create the booking and its booking–seat links, then commit.

**Timeline: Alice and Bob both want A1 (currently version 7):**

```
time  Alice                                      Bob
 t1   reads A1: AVAILABLE, v7                    reads A1: AVAILABLE, v7
 t2   UPDATE … WHERE version=7 → 1 row ✔
      (Alice now holds A1's row lock)
 t3                                              UPDATE … WHERE version=7 → waits for lock…
 t4   COMMIT (A1 is HELD, v8)
 t5                                              re-checks: version is 8, not 7 → 0 rows ✘
 t6                                              rollback → 409 "seat no longer available"
```

Both passed the Java check at t1, but only one wins. The database's atomic `UPDATE` makes the
final decision, not the Java code.

**All or nothing:** Bob asks for A1, A2 and A3, and only A1 was taken. His whole transaction
rolls back, so A2 and A3 stay `AVAILABLE`.

**Deadlock prevention:** every request updates its seats in the same order (by seat ID), so two
overlapping requests can't each hold a seat the other needs. If PostgreSQL ever does report a
deadlock, it is also returned as a 409.

**Two kinds of 409:**
- "Seats not available: A1": the seat was already HELD/BOOKED when we read it (the Java check, step 2).
- "Seats were just taken by another booking": we read it as AVAILABLE, but another request committed first (the version check, step 4).

---

## 3. Optimistic vs pessimistic, and why optimistic

| | Optimistic (version column) | Pessimistic (`SELECT … FOR UPDATE`) |
|---|---|---|
| When the lock is taken | Only for the instant of the `UPDATE` | From the read until commit |
| The loser | Fails fast with a 409 | Waits, then sees the seat is HELD and gets a 409 anyway |
| Deadlock risk | Low (short locks, fixed order) | Higher (locks held longer) |
| Best when | Conflicts are uncommon | Conflicts are very common, and waiting leads somewhere useful |

**Why optimistic fits this app:**
1. **Waiting is pointless here.** If Bob loses A1, waiting can't get it for him. He needs to pick another seat, so failing fast is the right answer.
2. **Most requests don't collide.** Many seats and many people usually means different seats, so most holds pay no locking cost.
3. **The `version` column already exists** and Hibernate handles it automatically.
4. **It also protects the other changes.** Confirm, cancel and the expiry job all update seats and bookings through version checks. None of them can silently overwrite another.

**When pessimistic would be better:** a huge on-sale, like a stadium tour, where thousands of
people want the same few seats. Most optimistic attempts would fail there. In that case you'd
lock the rows, or put requests in a queue. It's a trade-off, not a rule.

---

## 4. Database (Flyway V4)

**`bookings`:**
- `id`
- `user_id`: FK to `users`
- `event_id`: FK to `events`
- `status`: CHECK limits it to HELD, CONFIRMED, CANCELLED or EXPIRED
- `total_cents`, `held_until`, `created_at`
- `version`: optimistic lock on the booking itself
- **Indexes:** `(user_id)` for "my bookings", and `(status, held_until)` so the expiry job can find expired holds fast.

**`booking_seats`:** links bookings to seats.
- `booking_id` and `seat_id` together form the primary key.
- It keeps history: a cancelled booking still shows which seats it had.

**Why `bookings` has a `version` too:** it stops a race between **confirm** and the **expiry job**.
If the user confirms at 9:59.9 while the job expires the hold at 10:00.0, only one of them succeeds.

---

## 5. Endpoints and rules

All `/api/bookings` endpoints require a JWT (JSON Web Token).

**`POST /api/bookings` with body `{eventId, seatIds}`:**
- 1–6 distinct seat IDs, all belonging to that event. Otherwise 400.
- Event not found → 404.
- Any seat not `AVAILABLE` → 409, and nothing is held.
- On success: 201. Seats become `HELD`, `held_until` is set to now + 10 minutes, and the total is the sum of the seat prices.

**`POST /api/bookings/{id}/confirm`:**
- Allowed only if the booking is `HELD` and `held_until` is still in the future.
- Seats become `BOOKED` and the booking becomes `CONFIRMED`.
- An expired hold or a booking in any other status → 409.
- This checks `held_until` itself, so it doesn't depend on the job having already run.

**`POST /api/bookings/{id}/cancel`:**
- Allowed for `HELD` and `CONFIRMED` bookings.
- The booking becomes `CANCELLED` and its seats go back to `AVAILABLE` (on sale again).
- Refunds are out of scope.

**`GET /api/bookings`:** your bookings, newest first, each with its seats.

**Ownership:** the booking is looked up with both the booking ID *and* your user ID. Another
user's booking returns **404, not 403 (Forbidden)**, so nobody can probe which booking IDs exist.

**Scheduled expiry job (every 30 seconds):**
- Finds `HELD` bookings whose `held_until` has passed, marks them `EXPIRED` and frees their seats.
- Each booking is handled in its own transaction. If one hits a version conflict (the user confirmed or cancelled at that moment), the job skips it and carries on.
- If the app ever runs as two copies, both jobs can run safely because the version check makes sure only one wins.
- A seat can stay `HELD` up to about 30 seconds past its 10 minutes before it's freed. Confirming is blocked on time anyway, so this only delays when the seat goes back on sale.

### Booking state machine

```
            hold (POST /api/bookings)
                     │
                     ▼
       ┌──────────  HELD  ──────────┐
       │             │              │
  confirm        cancel       held_until passes
 (in time)           │         (expiry job)
       │             ▼              ▼
       ▼         CANCELLED       EXPIRED
   CONFIRMED ──cancel──▶ CANCELLED
```

Seat status follows the booking: `HELD` → seats `HELD`; `CONFIRMED` → seats `BOOKED`;
`CANCELLED` / `EXPIRED` → seats `AVAILABLE`.

---

## 6. Likely interview questions

**Q: Why not just check `status == AVAILABLE` in Java?**
Two requests can both read AVAILABLE before either saves. The check alone is a race condition.
The version check in the `UPDATE` is what makes it safe, because the database does it atomically.

**Q: What happens if the app crashes halfway through a hold?**
The transaction never commits, so PostgreSQL rolls everything back. No seat is left half-held.

**Q: Why return 404 instead of 403 for someone else's booking?**
403 would confirm the booking exists. 404 reveals nothing.

**Q: What if two copies of the app run the expiry job at the same time?**
Both try to update the same booking. The version check lets one succeed. The other gets a
conflict, skips that booking, and moves on.

**Q: How would you scale this for a huge on-sale?**
Use pessimistic locking or a queue for the hot seats, add rate limiting, and maybe a fast
in-memory store like Redis for holds, with the database still as the final source of truth.
