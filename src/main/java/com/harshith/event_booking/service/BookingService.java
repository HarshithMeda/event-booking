package com.harshith.event_booking.service;

import com.harshith.event_booking.cache.SeatsChangedEvent;
import com.harshith.event_booking.dto.BookingResponse;
import com.harshith.event_booking.dto.HoldRequest;
import com.harshith.event_booking.exception.BookingNotFoundException;
import com.harshith.event_booking.exception.BookingStateException;
import com.harshith.event_booking.exception.EventNotFoundException;
import com.harshith.event_booking.exception.InvalidBookingRequestException;
import com.harshith.event_booking.exception.SeatUnavailableException;
import com.harshith.event_booking.model.Booking;
import com.harshith.event_booking.model.BookingStatus;
import com.harshith.event_booking.model.Event;
import com.harshith.event_booking.model.Seat;
import com.harshith.event_booking.model.SeatStatus;
import com.harshith.event_booking.repository.BookingRepository;
import com.harshith.event_booking.repository.EventRepository;
import com.harshith.event_booking.repository.SeatRepository;
import com.harshith.event_booking.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Holds, confirms and cancels seats. Double booking is prevented with optimistic locking:
 * every seat and booking update runs "UPDATE ... WHERE id = ? AND version = ?", so if two
 * requests change the same row, only the first to commit wins. See docs/booking-design.md.
 *
 * This class never reads the cache: seat availability always comes from the database.
 * After each change it publishes SeatsChangedEvent, which clears the cached seat map after commit.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookingRepository;
    private final SeatRepository seatRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;
    private final Duration holdDuration;

    public BookingService(BookingRepository bookingRepository, SeatRepository seatRepository,
                          EventRepository eventRepository, UserRepository userRepository,
                          ApplicationEventPublisher events,
                          @Value("${app.booking.hold-duration:PT10M}") Duration holdDuration) {
        this.bookingRepository = bookingRepository;
        this.seatRepository = seatRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.events = events;
        this.holdDuration = holdDuration;
    }

    /** Holds all requested seats, or none of them. */
    @Transactional
    public BookingResponse hold(Long userId, HoldRequest request) {
        Set<Long> seatIds = new HashSet<>(request.seatIds());
        if (seatIds.size() != request.seatIds().size()) {
            throw new InvalidBookingRequestException("The same seat was requested more than once");
        }
        Event event = eventRepository.findById(request.eventId())
                .orElseThrow(() -> new EventNotFoundException(request.eventId()));

        // Sorted by id: every request touches seats in the same order, so overlapping requests can't deadlock.
        List<Seat> seats = seatRepository.findByIdInOrderByIdAsc(seatIds);
        if (seats.size() != seatIds.size()) {
            Set<Long> found = seats.stream().map(Seat::getId).collect(Collectors.toSet());
            List<Long> missing = seatIds.stream().filter(id -> !found.contains(id)).sorted().toList();
            throw new InvalidBookingRequestException("Unknown seat ids: " + missing);
        }
        List<Seat> wrongEvent = seats.stream().filter(s -> !s.getEvent().getId().equals(event.getId())).toList();
        if (!wrongEvent.isEmpty()) {
            throw new InvalidBookingRequestException(
                    "Seats " + labels(wrongEvent) + " do not belong to event " + event.getId());
        }

        // Check 1 (fast path): the seat was already taken when we read it.
        List<Seat> unavailable = seats.stream().filter(s -> s.getStatus() != SeatStatus.AVAILABLE).toList();
        if (!unavailable.isEmpty()) {
            throw new SeatUnavailableException("Seats not available: " + labels(unavailable));
        }

        // Check 2 (the real guarantee): the version check when the UPDATE runs.
        // Flushing now runs "UPDATE seats ... WHERE id = ? AND version = ?" for each seat.
        // If another request committed first, 0 rows match, Hibernate throws, and this
        // whole transaction rolls back, so none of the seats end up held.
        seats.forEach(s -> s.setStatus(SeatStatus.HELD));
        try {
            seatRepository.saveAllAndFlush(seats);
        } catch (ConcurrencyFailureException e) {
            log.info("Hold conflict for user {} on seats {}: another booking committed first", userId, seatIds);
            throw new SeatUnavailableException("Seats were just taken by another booking: " + labels(seats));
        }

        int totalCents = seats.stream().mapToInt(Seat::getPriceCents).sum();
        List<Seat> displayOrder = seats.stream()
                .sorted(Comparator.comparing(Seat::getSeatRow).thenComparing(Seat::getSeatNumber))
                .toList();
        Booking booking = new Booking(userRepository.getReferenceById(userId), event, displayOrder,
                totalCents, Instant.now().plus(holdDuration));
        bookingRepository.save(booking);
        events.publishEvent(new SeatsChangedEvent(event.getId()));
        return BookingResponse.from(booking);
    }

    @Transactional
    public BookingResponse confirm(Long userId, Long bookingId) {
        Booking booking = findOwned(userId, bookingId);
        if (booking.getStatus() != BookingStatus.HELD) {
            throw new BookingStateException(
                    "Only HELD bookings can be confirmed; this booking is " + booking.getStatus());
        }
        // Checked here too, so an expired hold can't be confirmed even if the expiry job hasn't run yet.
        if (!booking.getHeldUntil().isAfter(Instant.now())) {
            throw new BookingStateException("The hold expired at " + booking.getHeldUntil());
        }
        requireSeatStatus(booking, SeatStatus.HELD);
        booking.getSeats().forEach(s -> s.setStatus(SeatStatus.BOOKED));
        booking.setStatus(BookingStatus.CONFIRMED);
        flush(booking);
        events.publishEvent(new SeatsChangedEvent(booking.getEvent().getId()));
        return BookingResponse.from(booking);
    }

    /** Cancels a HELD or CONFIRMED booking and puts its seats back on sale. */
    @Transactional
    public BookingResponse cancel(Long userId, Long bookingId) {
        Booking booking = findOwned(userId, bookingId);
        SeatStatus expected = switch (booking.getStatus()) {
            case HELD -> SeatStatus.HELD;
            case CONFIRMED -> SeatStatus.BOOKED;
            default -> throw new BookingStateException(
                    "Only HELD or CONFIRMED bookings can be cancelled; this booking is " + booking.getStatus());
        };
        requireSeatStatus(booking, expected);
        booking.getSeats().forEach(s -> s.setStatus(SeatStatus.AVAILABLE));
        booking.setStatus(BookingStatus.CANCELLED);
        flush(booking);
        events.publishEvent(new SeatsChangedEvent(booking.getEvent().getId()));
        return BookingResponse.from(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> listForUser(Long userId) {
        return bookingRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(BookingResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Long> findExpiredHoldIds() {
        return bookingRepository.findExpiredHoldIds(Instant.now());
    }

    /**
     * Expires one hold in its own transaction. Returns false if it no longer needs expiring.
     * Throws ConcurrencyFailureException if the user confirmed/cancelled it at the same moment.
     */
    @Transactional
    public boolean expireHold(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.HELD
                || booking.getHeldUntil().isAfter(Instant.now())) {
            return false;
        }
        booking.getSeats().forEach(s -> s.setStatus(SeatStatus.AVAILABLE));
        booking.setStatus(BookingStatus.EXPIRED);
        bookingRepository.saveAndFlush(booking);
        events.publishEvent(new SeatsChangedEvent(booking.getEvent().getId()));
        return true;
    }

    private Booking findOwned(Long userId, Long bookingId) {
        return bookingRepository.findByIdAndUserId(bookingId, userId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
    }

    /** Seats should always match the booking; if not, refuse rather than make things worse. */
    private static void requireSeatStatus(Booking booking, SeatStatus expected) {
        List<Seat> wrong = booking.getSeats().stream().filter(s -> s.getStatus() != expected).toList();
        if (!wrong.isEmpty()) {
            throw new BookingStateException("Seats " + labels(wrong) + " are not " + expected);
        }
    }

    /** Saves booking and seat changes now, turning a version conflict into a clear 409. */
    private void flush(Booking booking) {
        try {
            bookingRepository.saveAndFlush(booking);
        } catch (ConcurrencyFailureException e) {
            throw new BookingStateException(
                    "This booking was changed at the same time (for example, its hold just expired). Nothing was saved.");
        }
    }

    private static String labels(Collection<Seat> seats) {
        return seats.stream().map(s -> s.getSeatRow() + s.getSeatNumber()).collect(Collectors.joining(", "));
    }
}
