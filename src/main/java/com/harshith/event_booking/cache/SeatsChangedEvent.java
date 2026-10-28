package com.harshith.event_booking.cache;

/** Published when a booking holds, confirms, cancels or expires seats for an event. */
public record SeatsChangedEvent(Long eventId) {
}
