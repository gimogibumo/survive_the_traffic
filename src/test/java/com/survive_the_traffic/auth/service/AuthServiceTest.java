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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthService authService;

    private Clock clock;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        clock = Clock.fixed(Instant.parse("2026-10-20T13:30:00Z"), ZoneId.of("Asia/Seoul"));

        authService =
            new AuthService(
                userRepository,
                passwordEncoder,
                clock
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
            userRepository.saveAndFlush(any(User.class))
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
            .saveAndFlush(
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
            .isEqualTo(LocalDateTime.of(
                2026,10,20,22,30
            ));

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

    @Test
    void 사전_중복검사를_통과했더라도_DB에서_중복이_발생하면_중복이메일_예외로_변환한다() {

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
            userRepository.saveAndFlush(
                any(User.class)
            )
        ).willThrow(
            new DataIntegrityViolationException(
                "duplicate email"
            )
        );

        // when / then
        assertThatThrownBy(
            () -> authService.signup(request)
        )
            .isInstanceOf(
                DuplicateEmailException.class
            );
    }

    @Test
    void 이메일은_소문자로_정규화하여_저장한다() {

        // given
        SignupRequest request =
            new SignupRequest(
                "Kim@Example.COM",
                "password1234",
                "기범"
            );

        given(
            userRepository.existsByEmail(
                "kim@example.com"
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
            userRepository.saveAndFlush(
                any(User.class)
            )
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
        ArgumentCaptor<User> captor =
            ArgumentCaptor.forClass(
                User.class
            );

        verify(userRepository)
            .saveAndFlush(
                captor.capture()
            );

        assertThat(
            captor.getValue().getEmail()
        ).isEqualTo(
            "kim@example.com"
        );

        assertThat(
            response.email()
        ).isEqualTo(
            "kim@example.com"
        );
    }
}