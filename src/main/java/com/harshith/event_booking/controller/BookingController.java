package com.harshith.event_booking.controller;

import com.harshith.event_booking.dto.BookingResponse;
import com.harshith.event_booking.dto.HoldRequest;
import com.harshith.event_booking.security.TokenService;
import com.harshith.event_booking.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/** The user id always comes from the verified token, never from the request. */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> hold(@AuthenticationPrincipal Jwt jwt,
                                                @Valid @RequestBody HoldRequest request) {
        BookingResponse booking = bookingService.hold(TokenService.userId(jwt), request);
        return ResponseEntity.created(URI.create("/api/bookings/" + booking.id())).body(booking);
    }

    @PostMapping("/{id}/confirm")
    public BookingResponse confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return bookingService.confirm(TokenService.userId(jwt), id);
    }

    @PostMapping("/{id}/cancel")
    public BookingResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return bookingService.cancel(TokenService.userId(jwt), id);
    }

    @GetMapping
    public List<BookingResponse> myBookings(@AuthenticationPrincipal Jwt jwt) {
        return bookingService.listForUser(TokenService.userId(jwt));
    }
}
