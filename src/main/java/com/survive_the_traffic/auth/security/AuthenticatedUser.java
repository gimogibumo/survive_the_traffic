package com.survive_the_traffic.auth.security;

import com.survive_the_traffic.user.domain.User;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class AuthenticatedUser implements UserDetails, Serializable, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long userId;
    private final String email;
    private final String name;
    private String password;
    private final List<GrantedAuthority> authorities;

    private AuthenticatedUser(long userId, String email, String name, String password, List<GrantedAuthority> authorities) {
        this.userId = userId;
        this.email = email;
        this.name = name;
        this.password = password;
        this.authorities = authorities;
    }

    public static AuthenticatedUser from(
        User user
    ) {
        return new AuthenticatedUser(
            user.getId(),
            user.getEmail(),
            user.getName(),
            user.getPasswordHash(),
            List.of(
                new SimpleGrantedAuthority(
                    "ROLE_USER"
                )
            )
        );
    }

    public long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    @Override
    public void eraseCredentials() {
        this.password = null;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public @Nullable String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
