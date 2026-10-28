package com.harshith.event_booking.integration;

import com.harshith.event_booking.cache.CacheNames;
import com.harshith.event_booking.dto.SeatResponse;
import com.harshith.event_booking.model.SeatStatus;
import com.harshith.event_booking.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runs against a real Redis container. */
class SeatMapCacheIntegrationTest extends IntegrationTestBase {

    @Test
    void seatMapIsServedFromTheCache_untilABookingRefreshesIt() throws Exception {
        long event = rockNight();
        long a1 = seatId(event, "A1");
        long b5 = seatId(event, "B5");

        seatMap(event).andExpect(status().isOk());
        assertThat(seatMapCache().get(event)).as("cached after the first read").isNotNull();

        // Change the database behind the cache's back: the next read still shows the cached copy.
        jdbc.update("UPDATE seats SET status = 'BOOKED' WHERE id = ?", b5);
        seatMap(event).andExpect(jsonPath(statusOf(b5)).value(contains("AVAILABLE")));

        // A booking evicts the seat map once it commits, so the very next read is fresh.
        holdOk(tokenFor("alice@example.com"), event, a1);
        assertThat(seatMapCache().get(event)).as("evicted right after the hold").isNull();
        seatMap(event)
                .andExpect(jsonPath(statusOf(a1)).value(contains("HELD")))
                .andExpect(jsonPath(statusOf(b5)).value(contains("BOOKED")));
    }

    @Test
    void failedHold_rollsBack_andLeavesTheCacheAlone() throws Exception {
        long event = rockNight();
        long a1 = seatId(event, "A1");
        holdOk(tokenFor("alice@example.com"), event, a1);
        seatMap(event).andExpect(jsonPath(statusOf(a1)).value(contains("HELD")));

        hold(tokenFor("bob@example.com"), event, a1).andExpect(status().isConflict());

        assertThat(seatMapCache().get(event)).as("nothing changed, nothing evicted").isNotNull();
    }

    @Test
    void bookingNeverTrustsTheCache_evenWhenItIsStale() throws Exception {
        long event = rockNight();
        long a1 = seatId(event, "A1");
        holdOk(tokenFor("alice@example.com"), event, a1);

        // Plant a stale seat map claiming A1 is free.
        seatMapCache().put(event, List.of(new SeatResponse(a1, "A", 1, 7500, SeatStatus.AVAILABLE)));
        seatMap(event).andExpect(jsonPath(statusOf(a1)).value(contains("AVAILABLE")));

        // The hold reads the database, which says A1 is HELD.
        hold(tokenFor("bob@example.com"), event, a1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Seats not available: A1"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bookings", Integer.class)).isEqualTo(1);
    }

    @Test
    void eventDetailsAndPagesAreCached() throws Exception {
        long event = rockNight();
        mockMvc.perform(get("/api/events/{id}", event)).andExpect(status().isOk());
        mockMvc.perform(get("/api/events")).andExpect(status().isOk());

        assertThat(cacheManager.getCache(CacheNames.EVENTS).get(event)).isNotNull();
        assertThat(cacheManager.getCache(CacheNames.EVENT_PAGES).get("0:20")).isNotNull();

        // Renaming behind the cache's back proves the second read doesn't hit the database.
        jdbc.update("UPDATE events SET name = 'Renamed' WHERE id = ?", event);
        try {
            mockMvc.perform(get("/api/events/{id}", event)).andExpect(jsonPath("$.name").value("Rock Night Live"));
        } finally {
            jdbc.update("UPDATE events SET name = 'Rock Night Live' WHERE id = ?", event);
        }
    }

    private Cache seatMapCache() {
        return cacheManager.getCache(CacheNames.SEAT_MAPS);
    }

    private static String statusOf(long seatId) {
        return "$[?(@.id == %d)].status".formatted(seatId);
    }
}
