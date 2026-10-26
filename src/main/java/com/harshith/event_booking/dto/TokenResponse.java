package com.harshith.event_booking.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
