package com.harshith.event_booking.exception;

/** Same message for unknown email and wrong password, so callers can't tell which accounts exist. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
