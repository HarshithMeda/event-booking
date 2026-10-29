package com.harshith.event_booking.integration;

import com.harshith.event_booking.cache.CacheNames;
import com.harshith.event_booking.cache.StartupCacheClearer;
import com.harshith.event_booking.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runs against a real Redis container. */
class StartupCacheClearerIntegrationTest extends IntegrationTestBase {

    @Autowired
    StartupCacheClearer startupCacheClearer;

    @Test
    void startupClear_makesTheNextReadComeFromTheDatabase() throws Exception {
        long event = rockNight();
        mockMvc.perform(get("/api/events")).andExpect(status().isOk());
        mockMvc.perform(get("/api/events/{id}", event)).andExpect(status().isOk());
        seatMap(event).andExpect(status().isOk());
        assertThat(cacheManager.getCache(CacheNames.EVENT_PAGES).get("0:20")).isNotNull();
        assertThat(cacheManager.getCache(CacheNames.EVENTS).get(event)).isNotNull();
        assertThat(cacheManager.getCache(CacheNames.SEAT_MAPS).get(event)).isNotNull();

        // New data arrives behind the cache's back, like a migration in a new release.
        jdbc.update("UPDATE events SET name = 'Renamed by a release' WHERE id = ?", event);
        try {
            mockMvc.perform(get("/api/events/{id}", event)).andExpect(jsonPath("$.name").value("Rock Night Live"));

            startupCacheClearer.clearEventCaches();

            assertThat(cacheManager.getCache(CacheNames.EVENT_PAGES).get("0:20")).isNull();
            assertThat(cacheManager.getCache(CacheNames.EVENTS).get(event)).isNull();
            assertThat(cacheManager.getCache(CacheNames.SEAT_MAPS).get(event)).isNull();
            mockMvc.perform(get("/api/events/{id}", event)).andExpect(jsonPath("$.name").value("Renamed by a release"));
        } finally {
            jdbc.update("UPDATE events SET name = 'Rock Night Live' WHERE id = ?", event);
        }
    }
}
