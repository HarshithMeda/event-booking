package com.harshith.event_booking.integration;

import com.harshith.event_booking.dto.HoldRequest;
import com.harshith.event_booking.exception.SeatUnavailableException;
import com.harshith.event_booking.model.AppUser;
import com.harshith.event_booking.repository.UserRepository;
import com.harshith.event_booking.service.BookingService;
import com.harshith.event_booking.support.IntegrationTestBase;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real threads, real transactions, real PostgreSQL. Every thread waits at a starting gate,
 * then all of them try to hold at the same instant. Only the database decides who wins.
 */
class BookingConcurrencyIntegrationTest extends IntegrationTestBase {

    private static final int THREADS = 20;

    enum Outcome {
        WON,
        /** Read the seat as AVAILABLE, but another request committed first (version check). */
        LOST_VERSION_CHECK,
        /** The winner had already committed when this request read the seat. */
        SAW_SEAT_TAKEN
    }

    @Autowired
    BookingService bookingService;

    @Autowired
    UserRepository userRepository;

    @RepeatedTest(5)
    void manyThreadsHoldTheSameSeat_exactlyOneWins() throws Exception {
        long event = rockNight();
        long seat = seatId(event, "A1");

        Map<Outcome, Long> outcomes = race(i -> List.of(seat));

        assertThat(outcomes.get(Outcome.WON)).isEqualTo(1);
        assertThat(outcomes.getOrDefault(Outcome.LOST_VERSION_CHECK, 0L)
                + outcomes.getOrDefault(Outcome.SAW_SEAT_TAKEN, 0L)).isEqualTo(THREADS - 1);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM booking_seats WHERE seat_id = ?", Integer.class, seat))
                .as("bookings containing the seat").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bookings", Integer.class)).isEqualTo(1);
        assertThat(seatStatus(seat)).isEqualTo("HELD");
        System.out.printf("Same seat, %d threads: %s%n", THREADS, outcomes);
    }

    @Test
    void overlappingRequests_oneWins_andLosersLeaveTheirOtherSeatFree() throws Exception {
        long event = rockNight();
        long shared = seatId(event, "A1");
        // Thread i asks for the shared seat plus its own seat: B1..B10, C1..C10.
        List<Long> own = new ArrayList<>();
        for (String row : List.of("B", "C")) {
            for (int n = 1; n <= 10; n++) {
                own.add(seatId(event, row + n));
            }
        }

        Map<Outcome, Long> outcomes = race(i -> List.of(own.get(i), shared));

        assertThat(outcomes.get(Outcome.WON)).isEqualTo(1);
        long winnerOwnSeat = jdbc.queryForObject(
                "SELECT seat_id FROM booking_seats WHERE seat_id <> ?", Long.class, shared);
        for (long seat : own) {
            assertThat(seatStatus(seat)).as("seat %d", seat).isEqualTo(seat == winnerOwnSeat ? "HELD" : "AVAILABLE");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM booking_seats", Integer.class)).isEqualTo(2);
        System.out.printf("Overlapping seats, %d threads: %s%n", THREADS, outcomes);
    }

    /** Starts THREADS holds at the same moment; thread i asks for seatsFor(i). */
    private Map<Outcome, Long> race(IntFunction<List<Long>> seatsFor) throws Exception {
        long event = rockNight();
        List<Long> userIds = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            userIds.add(userRepository.save(new AppUser("racer" + i + "@example.com", "not-used")).getId());
        }

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                long userId = userIds.get(i);
                List<Long> seats = seatsFor.apply(i);
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        bookingService.hold(userId, new HoldRequest(event, seats));
                        return Outcome.WON;
                    } catch (SeatUnavailableException e) {
                        return e.getMessage().contains("just taken") ? Outcome.LOST_VERSION_CHECK : Outcome.SAW_SEAT_TAKEN;
                    }
                    // Anything else (a deadlock, a 500-style error) escapes and fails the test.
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            List<Outcome> results = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results.stream().collect(Collectors.groupingBy(o -> o, Collectors.counting()));
        } finally {
            pool.shutdownNow();
        }
    }
}
