package com.survive_the_traffic.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.survive_the_traffic.auth.dto.SignupRequest;
import com.survive_the_traffic.auth.dto.SignupResponse;
import com.survive_the_traffic.auth.exception.DuplicateEmailException;
import com.survive_the_traffic.user.domain.User;
import com.survive_the_traffic.user.repository.UserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        authService =
            new AuthService(
                userRepository,
                passwordEncoder
            );
    }

    @Test
    void 회원가입시_비밀번호를_암호화하여_저장한다() {

        // given
        SignupRequest request =
            new SignupRequest(
                "kim@example.com",
                "password1234",
                "기범"
            );

        given(
            userRepository.existsByEmail(
                request.email()
            )
        ).willReturn(false);

        given(
            passwordEncoder.encode(
                request.password()
            )
        ).willReturn(
            "encoded-password"
        );

        given(
            userRepository.save(any(User.class))
        ).willAnswer(
            invocation -> {

                User user =
                    invocation.getArgument(0);

                user.setId(1L);

                return user;
            }
        );

        // when
        SignupResponse response =
            authService.signup(request);

        // then
        ArgumentCaptor<User> userCaptor =
            ArgumentCaptor.forClass(
                User.class
            );

        verify(userRepository)
            .save(
                userCaptor.capture()
            );

        User savedUser =
            userCaptor.getValue();

        assertThat(savedUser.getEmail())
            .isEqualTo(
                "kim@example.com"
            );

        assertThat(savedUser.getName())
            .isEqualTo(
                "기범"
            );

        assertThat(savedUser.getPasswordHash())
            .isEqualTo(
                "encoded-password"
            );

        assertThat(savedUser.getPasswordHash())
            .isNotEqualTo(
                "password1234"
            );

        assertThat(savedUser.getCreatedAt())
            .isNotNull();

        assertThat(response.id())
            .isEqualTo(1L);

        assertThat(response.email())
            .isEqualTo(
                "kim@example.com"
            );

        assertThat(response.name())
            .isEqualTo(
                "기범"
            );
    }

    @Test
    void 이미_존재하는_이메일이면_회원가입할수없다() {

        // given
        SignupRequest request =
            new SignupRequest(
                "kim@example.com",
                "password1234",
                "기범"
            );

        given(
            userRepository.existsByEmail(
                request.email()
            )
        ).willReturn(true);

        // when / then
        assertThatThrownBy(
            () -> authService.signup(request)
        )
            .isInstanceOf(
                DuplicateEmailException.class
            );
    }
}