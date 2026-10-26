package com.harshith.event_booking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record HoldRequest(
        @NotNull Long eventId,
        @NotNull @Size(min = 1, max = 6) List<@NotNull Long> seatIds) {
}
