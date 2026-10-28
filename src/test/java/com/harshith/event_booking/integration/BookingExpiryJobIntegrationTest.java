package com.harshith.event_booking.integration;

import com.harshith.event_booking.cache.CacheNames;
import com.harshith.event_booking.job.BookingExpiryJob;
import com.harshith.event_booking.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The scheduler is off in tests, so each test runs the job itself at the right moment. */
class BookingExpiryJobIntegrationTest extends IntegrationTestBase {

    @Autowired
    BookingExpiryJob expiryJob;

    @Test
    void overdueHold_isExpired_seatIsFreed_andTheSeatMapShowsIt() throws Exception {
        String alice = tokenFor("alice@example.com");
        long event = rockNight();
        long a1 = seatId(event, "A1");
        long booking = holdOk(alice, event, a1);
        seatMap(event).andExpect(jsonPath("$[?(@.id == %d)].status".formatted(a1)).value(contains("HELD")));
        assertThat(cacheManager.getCache(CacheNames.SEAT_MAPS).get(event)).isNotNull();

        makeOverdue(booking);
        expiryJob.releaseExpiredHolds();

        assertThat(bookingStatus(booking)).isEqualTo("EXPIRED");
        assertThat(seatStatus(a1)).isEqualTo("AVAILABLE");
        assertThat(cacheManager.getCache(CacheNames.SEAT_MAPS).get(event)).as("seat map evicted").isNull();
        seatMap(event).andExpect(jsonPath("$[?(@.id == %d)].status".formatted(a1)).value(contains("AVAILABLE")));

        // The seat is back on sale.
        holdOk(tokenFor("bob@example.com"), event, a1);
    }

    @Test
    void holdsWithTimeLeft_areNotTouched() throws Exception {
        String alice = tokenFor("alice@example.com");
        long overdue = holdOk(alice, rockNight(), seatId(rockNight(), "A1"));
        long fresh = holdOk(alice, rockNight(), seatId(rockNight(), "A2"));
        makeOverdue(overdue);

        expiryJob.releaseExpiredHolds();

        assertThat(bookingStatus(overdue)).isEqualTo("EXPIRED");
        assertThat(bookingStatus(fresh)).isEqualTo("HELD");
        assertThat(seatStatus(seatId(rockNight(), "A2"))).isEqualTo("HELD");
    }

    @Test
    void confirmedBookings_areNeverExpired() throws Exception {
        String alice = tokenFor("alice@example.com");
        long a1 = seatId(rockNight(), "A1");
        long booking = holdOk(alice, rockNight(), a1);
        bookingAction(alice, booking, "confirm").andExpect(status().isOk());
        makeOverdue(booking);

        expiryJob.releaseExpiredHolds();

        assertThat(bookingStatus(booking)).isEqualTo("CONFIRMED");
        assertThat(seatStatus(a1)).isEqualTo("BOOKED");
    }

    @Test
    void overdueHold_cannotBeConfirmed_evenBeforeTheJobRuns() throws Exception {
        String alice = tokenFor("alice@example.com");
        long booking = holdOk(alice, rockNight(), seatId(rockNight(), "A1"));
        makeOverdue(booking);

        bookingAction(alice, booking, "confirm")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("expired")));
        assertThat(bookingStatus(booking)).isEqualTo("HELD"); // job hasn't run yet

        expiryJob.releaseExpiredHolds();
        assertThat(bookingStatus(booking)).isEqualTo("EXPIRED");
    }

    private void makeOverdue(long bookingId) {
        jdbc.update("UPDATE bookings SET held_until = now() - interval '1 minute' WHERE id = ?", bookingId);
    }
}
