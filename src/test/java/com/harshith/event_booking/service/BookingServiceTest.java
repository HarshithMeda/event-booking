package com.harshith.event_booking.service;

import com.harshith.event_booking.cache.SeatsChangedEvent;
import com.harshith.event_booking.dto.BookingResponse;
import com.harshith.event_booking.dto.HoldRequest;
import com.harshith.event_booking.dto.SeatResponse;
import com.harshith.event_booking.exception.BookingNotFoundException;
import com.harshith.event_booking.exception.BookingStateException;
import com.harshith.event_booking.exception.EventNotFoundException;
import com.harshith.event_booking.exception.InvalidBookingRequestException;
import com.harshith.event_booking.exception.SeatUnavailableException;
import com.harshith.event_booking.model.AppUser;
import com.harshith.event_booking.model.Booking;
import com.harshith.event_booking.model.BookingStatus;
import com.harshith.event_booking.model.Event;
import com.harshith.event_booking.model.Seat;
import com.harshith.event_booking.model.SeatStatus;
import com.harshith.event_booking.repository.BookingRepository;
import com.harshith.event_booking.repository.EventRepository;
import com.harshith.event_booking.repository.SeatRepository;
import com.harshith.event_booking.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    private static final long USER_ID = 7L;
    private static final long OTHER_USER_ID = 8L;
    private static final long EVENT_ID = 1L;
    private static final long BOOKING_ID = 100L;
    private static final Duration HOLD = Duration.ofMinutes(10);

    @Mock BookingRepository bookingRepository;
    @Mock SeatRepository seatRepository;
    @Mock EventRepository eventRepository;
    @Mock UserRepository userRepository;
    @Mock ApplicationEventPublisher events;

    private BookingService service;
    private Event event;
    private AppUser user;

    @BeforeEach
    void setUp() {
        service = new BookingService(bookingRepository, seatRepository, eventRepository, userRepository, events, HOLD);
        event = event(EVENT_ID, "Rock Night Live");
        user = new AppUser("alice@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", USER_ID);
    }

    // ---------------------------------------------------------------- hold

    @Nested
    class Hold {

        @Test
        void holdsAllSeats_savesSeatsBeforeBooking_andAnnouncesTheChange() {
            Seat a1 = seat(11L, event, "A", 1, 7500, SeatStatus.AVAILABLE);
            Seat c3 = seat(12L, event, "C", 3, 4500, SeatStatus.AVAILABLE);
            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
            when(seatRepository.findByIdInOrderByIdAsc(anyCollection())).thenReturn(List.of(a1, c3));
            when(userRepository.getReferenceById(USER_ID)).thenReturn(user);

            Instant before = Instant.now();
            BookingResponse response = service.hold(USER_ID, new HoldRequest(EVENT_ID, List.of(11L, 12L)));

            assertThat(response.status()).isEqualTo(BookingStatus.HELD);
            assertThat(response.totalCents()).isEqualTo(12000);
            assertThat(response.heldUntil()).isCloseTo(before.plus(HOLD), within(Duration.ofSeconds(5)));
            assertThat(response.seats()).extracting(SeatResponse::status).containsOnly(SeatStatus.HELD);
            assertThat(a1.getStatus()).isEqualTo(SeatStatus.HELD);
            assertThat(c3.getStatus()).isEqualTo(SeatStatus.HELD);

            // The versioned seat UPDATEs must run (and possibly fail) before the booking is created.
            InOrder order = inOrder(seatRepository, bookingRepository, events);
            order.verify(seatRepository).saveAllAndFlush(List.of(a1, c3));
            order.verify(bookingRepository).save(any(Booking.class));
            order.verify(events).publishEvent(new SeatsChangedEvent(EVENT_ID));
        }

        @Test
        void rejectsTheSameSeatTwice_beforeTouchingTheDatabase() {
            assertThatThrownBy(() -> service.hold(USER_ID, new HoldRequest(EVENT_ID, List.of(11L, 11L))))
                    .isInstanceOf(InvalidBookingRequestException.class)
                    .hasMessageContaining("more than once");
            verifyNoInteractions(eventRepository, seatRepository, bookingRepository, events);
        }

        @Test
        void unknownEvent_is404() {
            when(eventRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.hold(USER_ID, new HoldRequest(99L, List.of(11L))))
                    .isInstanceOf(EventNotFoundException.class);
            verifyNoInteractions(seatRepository, bookingRepository);
        }

        @Test
        void unknownSeatId_isRejected() {
            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
            when(seatRepository.findByIdInOrderByIdAsc(anyCollection()))
                    .thenReturn(List.of(seat(11L, event, "A", 1, 7500, SeatStatus.AVAILABLE)));

            assertThatThrownBy(() -> service.hold(USER_ID, new HoldRequest(EVENT_ID, List.of(11L, 999L))))
                    .isInstanceOf(InvalidBookingRequestException.class)
                    .hasMessageContaining("999");
            verify(seatRepository, never()).saveAllAndFlush(anyList());
        }

        @Test
        void seatFromAnotherEvent_isRejected() {
            Event otherEvent = event(2L, "Jazz in the Park");
            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
            when(seatRepository.findByIdInOrderByIdAsc(anyCollection()))
                    .thenReturn(List.of(seat(21L, otherEvent, "B", 4, 7500, SeatStatus.AVAILABLE)));

            assertThatThrownBy(() -> service.hold(USER_ID, new HoldRequest(EVENT_ID, List.of(21L))))
                    .isInstanceOf(InvalidBookingRequestException.class)
                    .hasMessageContaining("B4 do not belong to event 1");
            verify(seatRepository, never()).saveAllAndFlush(anyList());
        }

        @Test
        void seatAlreadyTaken_is409_andNothingIsHeld() {
            Seat free = seat(11L, event, "A", 1, 7500, SeatStatus.AVAILABLE);
            Seat taken = seat(12L, event, "A", 2, 7500, SeatStatus.BOOKED);
            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
            when(seatRepository.findByIdInOrderByIdAsc(anyCollection())).thenReturn(List.of(free, taken));

            assertThatThrownBy(() -> service.hold(USER_ID, new HoldRequest(EVENT_ID, List.of(11L, 12L))))
                    .isInstanceOf(SeatUnavailableException.class)
                    .hasMessage("Seats not available: A2");
            assertThat(free.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
            verify(seatRepository, never()).saveAllAndFlush(anyList());
            verifyNoInteractions(bookingRepository, events);
        }

        @Test
        void losingTheVersionCheck_is409_andNoBookingIsCreated() {
            // Both requests read the seat as AVAILABLE; the other one committed first,
            // so our "UPDATE ... WHERE version = ?" matched 0 rows.
            Seat a1 = seat(11L, event, "A", 1, 7500, SeatStatus.AVAILABLE);
            when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
            when(seatRepository.findByIdInOrderByIdAsc(anyCollection())).thenReturn(List.of(a1));
            when(seatRepository.saveAllAndFlush(anyList()))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Seat.class, 11L));

            assertThatThrownBy(() -> service.hold(USER_ID, new HoldRequest(EVENT_ID, List.of(11L))))
                    .isInstanceOf(SeatUnavailableException.class)
                    .hasMessageContaining("just taken by another booking");
            verifyNoInteractions(bookingRepository, events);
        }
    }

    // ---------------------------------------------------------------- confirm

    @Nested
    class Confirm {

        @Test
        void confirmsAHeldBooking_seatsBecomeBooked() {
            Seat a1 = seat(11L, event, "A", 1, 7500, SeatStatus.HELD);
            Booking booking = booking(List.of(a1), Instant.now().plusSeconds(300));
            when(bookingRepository.findByIdAndUserId(BOOKING_ID, USER_ID)).thenReturn(Optional.of(booking));

            BookingResponse response = service.confirm(USER_ID, BOOKING_ID);

            assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(a1.getStatus()).isEqualTo(SeatStatus.BOOKED);
            verify(bookingRepository).saveAndFlush(booking);
            verify(events).publishEvent(new SeatsChangedEvent(EVENT_ID));
        }

        @Test
        void expiredHold_cannotBeConfirmed_evenBeforeTheJobRuns() {
            Seat a1 = seat(11L, event, "A", 1, 7500, SeatStatus.HELD);
            Booking booking = booking(List.of(a1), Instant.now().minusSeconds(1));
            when(bookingRepository.findByIdAndUserId(BOOKING_ID, USER_ID)).thenReturn(Optional.of(booking));

            assertThatThrownBy(() -> service.confirm(USER_ID, BOOKING_ID))
                    .isInstanceOf(BookingStateException.class)
                    .hasMessageContaining("expired");
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.HELD);
            assertThat(a1.getStatus()).isEqualTo(SeatStatus.HELD);
            verify(bookingRepository, never()).saveAndFlush(any());
            verifyNoInteractions(events);
        }

        @Test
        void onlyHeldBookingsCanBeConfirmed() {
            Booking booking = booking(List.of(seat(11L, event, "A", 1, 7500, SeatStatus.BOOKED)),
                    Instant.now().plusSeconds(300));
            booking.setStatus(BookingStatus.CONFIRMED);
            when(bookingRepository.findByIdAndUserId(BOOKING_ID, USER_ID)).thenReturn(Optional.of(booking));

            assertThatThrownBy(() -> service.confirm(USER_ID, BOOKING_ID))
                    .isInstanceOf(BookingStateException.class)
                    .hasMessageContaining("this booking is CONFIRMED");
        }

        @Test
        void someoneElsesBooking_isNotFound() {
            when(bookingRepository.findByIdAndUserId(BOOKING_ID, OTHER_USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.confirm(OTHER_USER_ID, BOOKING_ID))
                    .isInstanceOf(BookingNotFoundException.class);
            verify(bookingRepository, never()).saveAndFlush(any());
        }

        @Test
        void concurrentChange_is409() {
            Booking booking = booking(List.of(seat(11L, event, "A", 1, 7500, SeatStatus.HELD)),
                    Instant.now().plusSeconds(300));
            when(bookingRepository.findByIdAndUserId(BOOKING_ID, USER_ID)).thenReturn(Optional.of(booking));
            when(bookingRepository.saveAndFlush(booking))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Booking.class, BOOKING_ID));

            assertThatThrownBy(() -> service.confirm(USER_ID, BOOKING_ID))
                    .isInstanceOf(BookingStateException.class)
                    .hasMessageContaining("changed at the same time");
            verifyNoInteractions(events);
        }
    }

    // ---------------------------------------------------------------- cancel

    @Nested
    class Cancel {

        @Test
        void cancellingAHold_releasesSeats() {
            Seat a1 = seat(11L, event, "A", 1, 7500, SeatStatus.HELD);
            Booking booking = booking(List.of(a1), Instant.now().plusSeconds(300));
            when(bookingRepository.findByIdAndUserId(BOOKING_ID, USER_ID)).thenReturn(Optional.of(booking));

            assertThat(service.cancel(USER_ID, BOOKING_ID).status()).isEqualTo(BookingStatus.CANCELLED);
            assertThat(a1.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
            verify(events).publishEvent(new SeatsChangedEvent(EVENT_ID));
        }

        @Test
        void cancellingAConfirmedBooking_putsSeatsBackOnSale() {
            Seat a1 = seat(11L, event, "A", 1, 7500, SeatStatus.BOOKED);
            Booking booking = booking(List.of(a1), Instant.now().minusSeconds(600));
            booking.setStatus(BookingStatus.CONFIRMED);
            when(bookingRepository.findByIdAndUserId(BOOKING_ID, USER_ID)).thenReturn(Optional.of(booking));

            assertThat(service.cancel(USER_ID, BOOKING_ID).status()).isEqualTo(BookingStatus.CANCELLED);
            assertThat(a1.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        }

        @Test
        void expiredOrCancelledBookings_cannotBeCancelled() {
            Booking booking = booking(List.of(seat(11L, event, "A", 1, 7500, SeatStatus.AVAILABLE)),
                    Instant.now().minusSeconds(60));
            booking.setStatus(BookingStatus.EXPIRED);
            when(bookingRepository.findByIdAndUserId(BOOKING_ID, USER_ID)).thenReturn(Optional.of(booking));

            assertThatThrownBy(() -> service.cancel(USER_ID, BOOKING_ID))
                    .isInstanceOf(BookingStateException.class)
                    .hasMessageContaining("this booking is EXPIRED");
            verifyNoInteractions(events);
        }

        @Test
        void someoneElsesBooking_isNotFound() {
            when(bookingRepository.findByIdAndUserId(BOOKING_ID, OTHER_USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.cancel(OTHER_USER_ID, BOOKING_ID))
                    .isInstanceOf(BookingNotFoundException.class);
        }
    }

    // ---------------------------------------------------------------- expiry

    @Nested
    class Expiry {

        @Test
        void expiresAnOverdueHold_andReleasesSeats() {
            Seat a1 = seat(11L, event, "A", 1, 7500, SeatStatus.HELD);
            Booking booking = booking(List.of(a1), Instant.now().minusSeconds(1));
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));

            assertThat(service.expireHold(BOOKING_ID)).isTrue();
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.EXPIRED);
            assertThat(a1.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
            verify(bookingRepository).saveAndFlush(booking);
            verify(events).publishEvent(new SeatsChangedEvent(EVENT_ID));
        }

        @Test
        void leavesHoldsThatStillHaveTimeLeft() {
            Booking booking = booking(List.of(seat(11L, event, "A", 1, 7500, SeatStatus.HELD)),
                    Instant.now().plusSeconds(60));
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));

            assertThat(service.expireHold(BOOKING_ID)).isFalse();
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.HELD);
            verify(bookingRepository, never()).saveAndFlush(any());
        }

        @Test
        void leavesBookingsThatWereConfirmedInTheMeantime() {
            Booking booking = booking(List.of(seat(11L, event, "A", 1, 7500, SeatStatus.BOOKED)),
                    Instant.now().minusSeconds(1));
            booking.setStatus(BookingStatus.CONFIRMED);
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));

            assertThat(service.expireHold(BOOKING_ID)).isFalse();
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
            verifyNoInteractions(events);
        }
    }

    @Test
    void listForUser_onlyAsksForThatUsersBookings() {
        Booking booking = booking(List.of(seat(11L, event, "A", 1, 7500, SeatStatus.HELD)), Instant.now());
        when(bookingRepository.findByUserIdOrderByCreatedAtDescIdDesc(USER_ID)).thenReturn(List.of(booking));

        assertThat(service.listForUser(USER_ID)).extracting(BookingResponse::id).containsExactly(BOOKING_ID);
        ArgumentCaptor<Long> userId = ArgumentCaptor.forClass(Long.class);
        verify(bookingRepository).findByUserIdOrderByCreatedAtDescIdDesc(userId.capture());
        assertThat(userId.getValue()).isEqualTo(USER_ID);
    }

    // ---------------------------------------------------------------- helpers

    private static Event event(long id, String name) {
        Event event = new Event();
        ReflectionTestUtils.setField(event, "id", id);
        event.setName(name);
        event.setVenue("Venue");
        event.setStartsAt(Instant.parse("2026-12-05T19:30:00Z"));
        return event;
    }

    private static Seat seat(long id, Event event, String row, int number, int priceCents, SeatStatus status) {
        Seat seat = new Seat();
        ReflectionTestUtils.setField(seat, "id", id);
        seat.setEvent(event);
        seat.setSeatRow(row);
        seat.setSeatNumber(number);
        seat.setPriceCents(priceCents);
        seat.setStatus(status);
        return seat;
    }

    private Booking booking(List<Seat> seats, Instant heldUntil) {
        int total = seats.stream().mapToInt(Seat::getPriceCents).sum();
        Booking booking = new Booking(user, event, seats, total, heldUntil);
        ReflectionTestUtils.setField(booking, "id", BOOKING_ID);
        return booking;
    }
}
