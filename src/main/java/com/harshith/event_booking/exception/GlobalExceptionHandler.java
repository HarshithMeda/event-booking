package com.harshith.event_booking.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns exceptions into clean JSON error responses (RFC 9457 ProblemDetail).
 * Extending ResponseEntityExceptionHandler also gives standard 400s for bad input,
 * such as a non-numeric id or an out-of-range page size.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(EventNotFoundException.class)
    public ProblemDetail handleEventNotFound(EventNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Event not found");
        return problem;
    }
}
