package com.harshith.event_booking.controller;

import com.harshith.event_booking.dto.UserResponse;
import com.harshith.event_booking.security.TokenService;
import com.harshith.event_booking.service.AuthService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

    private final AuthService authService;

    public MeController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/api/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        Number uid = jwt.getClaim(TokenService.UID_CLAIM);
        return authService.currentUser(uid.longValue());
    }
}
