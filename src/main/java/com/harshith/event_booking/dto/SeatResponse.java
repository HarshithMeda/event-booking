package com.harshith.event_booking.dto;

import com.harshith.event_booking.model.Seat;
import com.harshith.event_booking.model.SeatStatus;

public record SeatResponse(Long id, String row, Integer number, Integer priceCents, SeatStatus status) {

    public static SeatResponse from(Seat seat) {
        return new SeatResponse(seat.getId(), seat.getSeatRow(), seat.getSeatNumber(),
                seat.getPriceCents(), seat.getStatus());
    }
}
