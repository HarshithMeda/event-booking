package com.harshith.event_booking.exception;

public class InvalidBookingRequestException extends RuntimeException {

    public InvalidBookingRequestException(String message) {
        super(message);
    }
}
