package com.harshith.event_booking.exception;

public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException(Long id) {
        super("Event not found: " + id);
    }
}
