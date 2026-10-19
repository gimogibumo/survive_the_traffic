package com.survive_the_traffic.auth.service;

import com.survive_the_traffic.auth.dto.LoginRequest;
import com.survive_the_traffic.auth.dto.LoginResponse;
import com.survive_the_traffic.auth.exception.InvalidCredentialsException;
import com.survive_the_traffic.auth.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SessionLoginService {

    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy
        sessionAuthenticationStrategy;
    private final SecurityContextRepository
        securityContextRepository;

    public LoginResponse login(
        LoginRequest loginRequest,
        HttpServletRequest request,
        HttpServletResponse response
    ) {

        String normalizedEmail =
            loginRequest.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        Authentication authenticationRequest =
            UsernamePasswordAuthenticationToken
                .unauthenticated(
                    normalizedEmail,
                    loginRequest.password()
                );

        Authentication authentication;

        try {
            authentication =
                authenticationManager.authenticate(
                    authenticationRequest
                );

        } catch (BadCredentialsException e) {
            throw new InvalidCredentialsException();
        }

        sessionAuthenticationStrategy
            .onAuthentication(
                authentication,
                request,
                response
            );

        SecurityContext context =
            SecurityContextHolder
                .createEmptyContext();

        context.setAuthentication(
            authentication
        );

        SecurityContextHolder.setContext(
            context
        );

        securityContextRepository.saveContext(
            context,
            request,
            response
        );

        AuthenticatedUser authenticatedUser =
            (AuthenticatedUser)
                authentication.getPrincipal();

        return LoginResponse.from(
            authenticatedUser
        );
    }
}