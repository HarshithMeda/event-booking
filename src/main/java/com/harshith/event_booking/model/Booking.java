package com.harshith.event_booking.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(name = "total_cents", nullable = false)
    private Integer totalCents;

    @Column(name = "held_until", nullable = false)
    private Instant heldUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // Optimistic lock: stops confirm, cancel and the expiry job from overwriting each other.
    @Version
    @Column(nullable = false)
    private Long version;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "booking_seats",
            joinColumns = @JoinColumn(name = "booking_id"),
            inverseJoinColumns = @JoinColumn(name = "seat_id"))
    @OrderBy("seatRow ASC, seatNumber ASC")
    private List<Seat> seats = new ArrayList<>();

    protected Booking() {
    }

    public Booking(AppUser user, Event event, List<Seat> seats, int totalCents, Instant heldUntil) {
        this.user = user;
        this.event = event;
        this.seats = new ArrayList<>(seats);
        this.totalCents = totalCents;
        this.heldUntil = heldUntil;
        this.status = BookingStatus.HELD;
    }

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public Event getEvent() { return event; }
    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }
    public Integer getTotalCents() { return totalCents; }
    public Instant getHeldUntil() { return heldUntil; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getVersion() { return version; }
    public List<Seat> getSeats() { return seats; }
}
