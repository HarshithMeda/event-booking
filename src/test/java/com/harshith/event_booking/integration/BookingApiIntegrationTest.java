package com.harshith.event_booking.integration;

import com.harshith.event_booking.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingApiIntegrationTest extends IntegrationTestBase {

    @Test
    void hold_confirm_list_cancel() throws Exception {
        String alice = tokenFor("alice@example.com");
        long event = rockNight();
        long a1 = seatId(event, "A1");
        long c1 = seatId(event, "C1");

        String body = hold(alice, event, a1, c1)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/bookings/")))
                .andExpect(jsonPath("$.status").value("HELD"))
                .andExpect(jsonPath("$.totalCents").value(7500 + 4500))
                .andExpect(jsonPath("$.seats[0].row").value("A"))
                .andReturn().getResponse().getContentAsString();
        long booking = ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.id")).longValue();
        assertThat(seatStatus(a1)).isEqualTo("HELD");
        assertThat(seatStatus(c1)).isEqualTo("HELD");

        bookingAction(alice, booking, "confirm")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertThat(seatStatus(a1)).isEqualTo("BOOKED");

        bookingAction(alice, booking, "confirm").andExpect(status().isConflict());

        mockMvc.perform(get("/api/bookings").header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"));

        bookingAction(alice, booking, "cancel")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(seatStatus(a1)).isEqualTo("AVAILABLE");
        assertThat(seatStatus(c1)).isEqualTo("AVAILABLE");

        bookingAction(alice, booking, "cancel").andExpect(status().isConflict());
    }

    @Test
    void anotherUsersBooking_is404_andIsLeftUntouched() throws Exception {
        String alice = tokenFor("alice@example.com");
        String bob = tokenFor("bob@example.com");
        long a1 = seatId(rockNight(), "A1");
        long booking = holdOk(alice, rockNight(), a1);

        bookingAction(bob, booking, "confirm")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Booking not found: " + booking));
        bookingAction(bob, booking, "cancel").andExpect(status().isNotFound());
        mockMvc.perform(get("/api/bookings").header("Authorization", bearer(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        assertThat(bookingStatus(booking)).isEqualTo("HELD");
        assertThat(seatStatus(a1)).isEqualTo("HELD");
    }

    @Test
    void holdIsAllOrNothing_whenOneSeatIsTaken() throws Exception {
        String alice = tokenFor("alice@example.com");
        String bob = tokenFor("bob@example.com");
        long a1 = seatId(rockNight(), "A1");
        long a2 = seatId(rockNight(), "A2");
        holdOk(alice, rockNight(), a1);

        hold(bob, rockNight(), a1, a2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Seats not available: A1"));

        assertThat(seatStatus(a2)).isEqualTo("AVAILABLE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bookings", Integer.class)).isEqualTo(1);
    }

    @Test
    void invalidHoldRequests_areRejected() throws Exception {
        String alice = tokenFor("alice@example.com");
        long event = rockNight();
        long[] seven = {seatId(event, "C1"), seatId(event, "C2"), seatId(event, "C3"), seatId(event, "C4"),
                seatId(event, "C5"), seatId(event, "C6"), seatId(event, "C7")};

        hold(alice, event).andExpect(status().isBadRequest());                     // 0 seats
        hold(alice, event, seven).andExpect(status().isBadRequest());              // 7 seats
        hold(alice, event, seven[0], seven[0]).andExpect(status().isBadRequest()); // same seat twice
        long otherEventSeat = seatId(eventId("Jazz in the Park"), "A1");
        hold(alice, event, otherEventSeat)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Seats A1 do not belong to event " + event));
        hold(alice, 999_999, seven[0]).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/bookings").header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"eventId\":" + event + "}"))
                .andExpect(status().isBadRequest());                               // seatIds missing

        assertThat(jdbc.queryForObject("SELECT count(*) FROM bookings", Integer.class)).isZero();
    }
}
