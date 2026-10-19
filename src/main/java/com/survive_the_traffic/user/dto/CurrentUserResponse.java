package com.survive_the_traffic.user.dto;

import com.survive_the_traffic.auth.security.AuthenticatedUser;

public record CurrentUserResponse(
    Long id,
    String email,
    String name
) {

    public static CurrentUserResponse from(
        AuthenticatedUser user
    ) {
        return new CurrentUserResponse(
            user.getUserId(),
            user.getEmail(),
            user.getName()
        );
    }
}