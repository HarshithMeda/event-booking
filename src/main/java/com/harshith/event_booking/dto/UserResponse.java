package com.harshith.event_booking.dto;

import com.harshith.event_booking.model.AppUser;

public record UserResponse(Long id, String email) {

    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getEmail());
    }
}
