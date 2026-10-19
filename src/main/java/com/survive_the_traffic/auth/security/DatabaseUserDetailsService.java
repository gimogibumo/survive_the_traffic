package com.survive_the_traffic.auth.security;

import com.survive_the_traffic.user.domain.User;
import com.survive_the_traffic.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DatabaseUserDetailsService
    implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(
        String email
    ) throws UsernameNotFoundException {

        User user =
            userRepository.findByEmail(email)
                .orElseThrow(
                    () ->
                        new UsernameNotFoundException(
                            "Invalid credentials"
                        )
                );

        if (
            user.getPasswordHash() == null
                || user.getPasswordHash().isBlank()
        ) {
            throw new UsernameNotFoundException(
                "Invalid credentials"
            );
        }

        return AuthenticatedUser.from(user);
    }
}