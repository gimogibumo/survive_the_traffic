package com.survive_the_traffic.auth.dto;

import com.survive_the_traffic.user.domain.User;

public record SignupResponse(
    Long id,
    String email,
    String name
) {
    public static SignupResponse from(User user) {
        return new SignupResponse(user.getId(), user.getEmail(), user.getName());
    }
}
