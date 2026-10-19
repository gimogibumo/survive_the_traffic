package com.survive_the_traffic.auth.service;

import com.survive_the_traffic.auth.dto.SignupRequest;
import com.survive_the_traffic.auth.dto.SignupResponse;
import com.survive_the_traffic.auth.exception.DuplicateEmailException;
import com.survive_the_traffic.user.domain.User;
import com.survive_the_traffic.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public SignupResponse signup(
        SignupRequest request
    ) {

        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        if (
            userRepository.existsByEmail(
                normalizedEmail
            )
        ) {
            throw new DuplicateEmailException();
        }

        String passwordHash =
            passwordEncoder.encode(
                request.password()
            );

        User user =
            User.builder()
                .email(normalizedEmail)
                .name(request.name())
                .passwordHash(passwordHash)
                .createdAt(LocalDateTime.now())
                .build();

        try {
            User savedUser =
                userRepository.saveAndFlush(user);

            return SignupResponse.from(
                savedUser
            );

        } catch (
            DataIntegrityViolationException e
        ) {
            throw new DuplicateEmailException();
        }
    }
}