package com.harshith.event_booking.support;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base for integration tests: the full app (security, JPA, Flyway, Redis cache) against real containers.
 * All subclasses share one Spring context, so the containers start only once per test run.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTestBase {

    protected static final String PASSWORD = "password123";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected CacheManager cacheManager;

    /** Each test starts from the seed data only: no users, no bookings, all seats available, empty caches. */
    @BeforeEach
    void resetState() {
        jdbc.execute("TRUNCATE booking_seats, bookings, users RESTART IDENTITY CASCADE");
        jdbc.update("UPDATE seats SET status = 'AVAILABLE'");
        for (String name : cacheManager.getCacheNames()) {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
    }

    // ---- data lookups (seed data from V2) ----

    protected long eventId(String name) {
        return jdbc.queryForObject("SELECT id FROM events WHERE name = ?", Long.class, name);
    }

    protected long rockNight() {
        return eventId("Rock Night Live");
    }

    /** Seat id from a label like "A1". */
    protected long seatId(long eventId, String label) {
        return jdbc.queryForObject(
                "SELECT id FROM seats WHERE event_id = ? AND seat_row = ? AND seat_number = ?",
                Long.class, eventId, label.substring(0, 1), Integer.parseInt(label.substring(1)));
    }

    protected String seatStatus(long seatId) {
        return jdbc.queryForObject("SELECT status FROM seats WHERE id = ?", String.class, seatId);
    }

    protected String bookingStatus(long bookingId) {
        return jdbc.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, bookingId);
    }

    // ---- API helpers ----

    protected ResultActions register(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    protected ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    /** Registers a user and returns a valid access token for them. */
    protected String tokenFor(String email) throws Exception {
        register(email, PASSWORD).andExpect(status().isCreated());
        String body = login(email, PASSWORD).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    protected ResultActions hold(String token, long eventId, long... seatIds) throws Exception {
        String ids = Arrays.stream(seatIds).mapToObj(Long::toString).collect(Collectors.joining(","));
        return mockMvc.perform(post("/api/bookings").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":%d,\"seatIds\":[%s]}".formatted(eventId, ids)));
    }

    /** Holds seats and returns the new booking id. */
    protected long holdOk(String token, long eventId, long... seatIds) throws Exception {
        String body = hold(token, eventId, seatIds).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    protected ResultActions bookingAction(String token, long bookingId, String action) throws Exception {
        return mockMvc.perform(post("/api/bookings/{id}/{action}", bookingId, action)
                .header("Authorization", bearer(token)));
    }

    protected ResultActions seatMap(long eventId) throws Exception {
        return mockMvc.perform(get("/api/events/{id}/seats", eventId));
    }
}
