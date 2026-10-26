CREATE TABLE bookings (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users(id),
    event_id     BIGINT      NOT NULL REFERENCES events(id),
    status       VARCHAR(20) NOT NULL
                 CHECK (status IN ('HELD', 'CONFIRMED', 'CANCELLED', 'EXPIRED')),
    total_cents  INT         NOT NULL CHECK (total_cents >= 0),
    held_until   TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    version      BIGINT      NOT NULL DEFAULT 0
);

-- "My bookings" lookups.
CREATE INDEX idx_bookings_user_id ON bookings(user_id);
-- The expiry job looks for HELD bookings whose held_until has passed.
CREATE INDEX idx_bookings_status_held_until ON bookings(status, held_until);

-- Which seats belong to which booking. Kept after cancel/expire as history.
CREATE TABLE booking_seats (
    booking_id  BIGINT NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    seat_id     BIGINT NOT NULL REFERENCES seats(id),
    PRIMARY KEY (booking_id, seat_id)
);

CREATE INDEX idx_booking_seats_seat_id ON booking_seats(seat_id);
