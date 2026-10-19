package com.survive_the_traffic.auth.dto;

import com.survive_the_traffic.auth.security.AuthenticatedUser;

public record LoginResponse(
    Long id,
    String email,
    String name
) {

    public static LoginResponse from(
        AuthenticatedUser user
    ) {
        return new LoginResponse(
            user.getUserId(),
            user.getEmail(),
            user.getName()
        );
    }
}