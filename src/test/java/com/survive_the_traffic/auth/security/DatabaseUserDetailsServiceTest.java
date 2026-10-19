package com.survive_the_traffic.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.survive_the_traffic.user.domain.User;
import com.survive_the_traffic.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class DatabaseUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    private DatabaseUserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        userDetailsService =
            new DatabaseUserDetailsService(
                userRepository
            );
    }

    @Test
    void 이메일로_인증사용자를_조회한다() {

        // given
        User user =
            User.builder()
                .id(1L)
                .email("kim@example.com")
                .name("기범")
                .passwordHash(
                    "$2a$10$encoded"
                )
                .createdAt(
                    LocalDateTime.now()
                )
                .build();

        given(
            userRepository.findByEmail(
                "kim@example.com"
            )
        ).willReturn(
            Optional.of(user)
        );

        // when
        AuthenticatedUser result =
            (AuthenticatedUser)
                userDetailsService
                    .loadUserByUsername(
                        "kim@example.com"
                    );

        // then
        assertThat(
            result.getUserId()
        ).isEqualTo(1L);

        assertThat(
            result.getUsername()
        ).isEqualTo(
            "kim@example.com"
        );

        assertThat(
            result.getName()
        ).isEqualTo(
            "기범"
        );

        assertThat(
            result.getPassword()
        ).isEqualTo(
            "$2a$10$encoded"
        );

        assertThat(
            result.getAuthorities()
        )
            .extracting(
                authority ->
                    authority.getAuthority()
            )
            .containsExactly(
                "ROLE_USER"
            );
    }

    @Test
    void 존재하지_않는_이메일이면_인증사용자를_조회할수없다() {

        // given
        given(
            userRepository.findByEmail(
                "missing@example.com"
            )
        ).willReturn(
            Optional.empty()
        );

        // when / then
        assertThatThrownBy(
            () ->
                userDetailsService
                    .loadUserByUsername(
                        "missing@example.com"
                    )
        )
            .isInstanceOf(
                UsernameNotFoundException.class
            );
    }

    @Test
    void 비밀번호가_없는_기존사용자는_로그인할수없다() {

        // given
        User user =
            User.builder()
                .id(1L)
                .email("legacy@example.com")
                .name("기존사용자")
                .passwordHash(null)
                .createdAt(
                    LocalDateTime.now()
                )
                .build();

        given(
            userRepository.findByEmail(
                "legacy@example.com"
            )
        ).willReturn(
            Optional.of(user)
        );

        // when / then
        assertThatThrownBy(
            () ->
                userDetailsService
                    .loadUserByUsername(
                        "legacy@example.com"
                    )
        )
            .isInstanceOf(
                UsernameNotFoundException.class
            );
    }
}