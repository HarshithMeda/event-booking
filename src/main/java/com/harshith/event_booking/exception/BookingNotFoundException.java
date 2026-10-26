package com.harshith.event_booking.exception;

public class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException(Long id) {
        super("Booking not found: " + id);
    }
}
