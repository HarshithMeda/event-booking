package com.harshith.event_booking.dto;

import com.harshith.event_booking.model.Booking;
import com.harshith.event_booking.model.BookingStatus;

import java.time.Instant;
import java.util.List;

public record BookingResponse(Long id, Long eventId, String eventName, BookingStatus status, Integer totalCents,
                              Instant heldUntil, Instant createdAt, List<SeatResponse> seats) {

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(booking.getId(), booking.getEvent().getId(), booking.getEvent().getName(),
                booking.getStatus(), booking.getTotalCents(), booking.getHeldUntil(), booking.getCreatedAt(),
                booking.getSeats().stream().map(SeatResponse::from).toList());
    }
}
