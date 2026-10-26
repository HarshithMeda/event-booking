package com.harshith.event_booking.dto;

import com.harshith.event_booking.model.Event;

import java.time.Instant;

public record EventResponse(Long id, String name, String venue, Instant startsAt) {

    public static EventResponse from(Event event) {
        return new EventResponse(event.getId(), event.getName(), event.getVenue(), event.getStartsAt());
    }
}
