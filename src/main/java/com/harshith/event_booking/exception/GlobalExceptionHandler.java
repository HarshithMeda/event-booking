package com.harshith.event_booking.exception;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;
import java.util.TreeMap;

/**
 * Turns exceptions into clean JSON error responses (RFC 9457 ProblemDetail).
 * Extending ResponseEntityExceptionHandler also gives standard 400s for bad input,
 * such as a non-numeric id or an out-of-range page size.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(EventNotFoundException.class)
    public ProblemDetail handleEventNotFound(EventNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Event not found", ex.getMessage());
    }

    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ProblemDetail handleEmailAlreadyUsed(EmailAlreadyUsedException ex) {
        return problem(HttpStatus.CONFLICT, "Email already registered", ex.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage());
    }

    @ExceptionHandler(BookingNotFoundException.class)
    public ProblemDetail handleBookingNotFound(BookingNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Booking not found", ex.getMessage());
    }

    @ExceptionHandler(SeatUnavailableException.class)
    public ProblemDetail handleSeatUnavailable(SeatUnavailableException ex) {
        return problem(HttpStatus.CONFLICT, "Seats unavailable", ex.getMessage());
    }

    @ExceptionHandler(BookingStateException.class)
    public ProblemDetail handleBookingState(BookingStateException ex) {
        return problem(HttpStatus.CONFLICT, "Booking cannot be changed", ex.getMessage());
    }

    @ExceptionHandler(InvalidBookingRequestException.class)
    public ProblemDetail handleInvalidBooking(InvalidBookingRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
    }

    /**
     * Safety net: a version conflict or deadlock that wasn't caught closer to the source.
     * The transaction has already been rolled back, so nothing was saved.
     */
    @ExceptionHandler(ConcurrencyFailureException.class)
    public ProblemDetail handleConcurrencyFailure(ConcurrencyFailureException ex) {
        return problem(HttpStatus.CONFLICT, "Conflict",
                "Someone else changed this at the same time. Nothing was saved; please try again.");
    }

    /** Adds which fields failed validation, e.g. {"errors": {"password": "size must be between 8 and 72"}}. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new TreeMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ProblemDetail body = ex.getBody();
        body.setDetail("Validation failed");
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
