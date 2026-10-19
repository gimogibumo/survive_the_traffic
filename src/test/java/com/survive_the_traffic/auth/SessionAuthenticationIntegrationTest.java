package com.survive_the_traffic.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.survive_the_traffic.auth.security.AuthenticatedUser;
import com.survive_the_traffic.auth.security.DatabaseUserDetailsService;
import com.survive_the_traffic.user.domain.User;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class SessionAuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private DatabaseUserDetailsService userDetailsService;

    @Test
    void 로그인후_같은세션의_다음요청에서_인증사용자가_복구된다()
        throws Exception {

        // given
        String rawPassword =
            "password1234";

        User user =
            User.builder()
                .id(1L)
                .email("kim@example.com")
                .name("기범")
                .passwordHash(
                    passwordEncoder.encode(
                        rawPassword
                    )
                )
                .createdAt(
                    LocalDateTime.now()
                )
                .build();

        given(
            userDetailsService.loadUserByUsername(
                "kim@example.com"
            )
        ).willReturn(
            AuthenticatedUser.from(user)
        );

        MockHttpSession initialSession =
            new MockHttpSession();

        String sessionIdBeforeLogin =
            initialSession.getId();

        /*
         * 실제 /api/csrf endpoint를 호출해서
         * XSRF-TOKEN cookie를 발급받는다.
         *
         * 이 통합 테스트에서는 with(csrf())를 사용하지 않는다.
         */
        CsrfCredentials csrf =
            issueCsrf(
                initialSession
            );

        // when
        MvcResult loginResult =
            mockMvc.perform(
                    post("/api/auth/login")
                        .session(
                            initialSession
                        )
                        .cookie(
                            csrf.cookie()
                        )
                        .header(
                            "X-XSRF-TOKEN",
                            csrf.token()
                        )
                        .contentType(
                            MediaType.APPLICATION_JSON
                        )
                        .content(
                            """
                            {
                              "email": "Kim@Example.COM",
                              "password": "password1234"
                            }
                            """
                        )
                )
                .andExpect(
                    status().isOk()
                )
                .andExpect(
                    jsonPath("$.id")
                        .value(1L)
                )
                .andExpect(
                    jsonPath("$.email")
                        .value(
                            "kim@example.com"
                        )
                )
                .andExpect(
                    jsonPath("$.name")
                        .value("기범")
                )
                .andReturn();

        /*
         * 1.
         * 로그인 성공 후 session이 존재하는지 확인
         */
        MockHttpSession authenticatedSession =
            (MockHttpSession)
                loginResult
                    .getRequest()
                    .getSession(false);

        assertThat(
            authenticatedSession
        ).isNotNull();

        /*
         * 2.
         * Session Fixation 방어가 동작했는지 확인
         *
         * 로그인 전 session id와
         * 로그인 후 session id가 달라야 한다.
         */
        assertThat(
            authenticatedSession.getId()
        ).isNotEqualTo(
            sessionIdBeforeLogin
        );

        /*
         * 3.
         * SecurityContext가 HttpSession에
         * 실제로 저장되었는지 확인
         */
        Object contextObject =
            authenticatedSession.getAttribute(
                HttpSessionSecurityContextRepository
                    .SPRING_SECURITY_CONTEXT_KEY
            );

        assertThat(
            contextObject
        ).isInstanceOf(
            SecurityContext.class
        );

        SecurityContext securityContext =
            (SecurityContext)
                contextObject;

        assertThat(
            securityContext
                .getAuthentication()
        ).isNotNull();

        assertThat(
            securityContext
                .getAuthentication()
                .isAuthenticated()
        ).isTrue();

        AuthenticatedUser principal =
            (AuthenticatedUser)
                securityContext
                    .getAuthentication()
                    .getPrincipal();

        assertThat(
            principal.getUserId()
        ).isEqualTo(1L);

        assertThat(
            principal.getEmail()
        ).isEqualTo(
            "kim@example.com"
        );

        assertThat(
            principal.getName()
        ).isEqualTo(
            "기범"
        );

        /*
         * 4.
         * 다음 HTTP 요청에서 같은 session을 보내면
         * Spring Security가 SecurityContext를 복구하고
         * @AuthenticationPrincipal을 정상 주입하는지 확인
         */
        mockMvc.perform(
                get("/api/users/me")
                    .session(
                        authenticatedSession
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.id")
                    .value(1L)
            )
            .andExpect(
                jsonPath("$.email")
                    .value(
                        "kim@example.com"
                    )
            )
            .andExpect(
                jsonPath("$.name")
                    .value("기범")
            );
    }

    @Test
    void CSRF_토큰없이_로그인하면_403을_반환한다()
        throws Exception {

        /*
         * CSRF cookie도 없고
         * X-XSRF-TOKEN header도 없는 POST 요청.
         *
         * Controller까지 도달하기 전에
         * CsrfFilter에서 차단되어야 한다.
         */
        mockMvc.perform(
                post("/api/auth/login")
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "email": "kim@example.com",
                          "password": "password1234"
                        }
                        """
                    )
            )
            .andExpect(
                status().isForbidden()
            );
    }

    @Test
    void 로그인후_기존_CSRF토큰을_버리고_새토큰을_발급받는다()
        throws Exception {

        // given
        String rawPassword =
            "password1234";

        User user =
            User.builder()
                .id(1L)
                .email("kim@example.com")
                .name("기범")
                .passwordHash(
                    passwordEncoder.encode(
                        rawPassword
                    )
                )
                .createdAt(
                    LocalDateTime.now()
                )
                .build();

        given(
            userDetailsService.loadUserByUsername(
                "kim@example.com"
            )
        ).willReturn(
            AuthenticatedUser.from(user)
        );

        MockHttpSession session =
            new MockHttpSession();

        /*
         * 1.
         * 로그인 전에 실제 CSRF token 발급
         */
        CsrfCredentials beforeLogin =
            issueCsrf(
                session
            );

        assertThat(
            beforeLogin.token()
        ).isNotBlank();

        /*
         * 2.
         * 실제 브라우저와 동일하게
         *
         * Cookie:
         * XSRF-TOKEN=<token>
         *
         * Header:
         * X-XSRF-TOKEN: <token>
         *
         * 를 함께 전송한다.
         */
        MvcResult loginResult =
            mockMvc.perform(
                    post("/api/auth/login")
                        .session(
                            session
                        )
                        .cookie(
                            beforeLogin.cookie()
                        )
                        .header(
                            "X-XSRF-TOKEN",
                            beforeLogin.token()
                        )
                        .contentType(
                            MediaType.APPLICATION_JSON
                        )
                        .content(
                            """
                            {
                              "email": "kim@example.com",
                              "password": "password1234"
                            }
                            """
                        )
                )
                .andExpect(
                    status().isOk()
                )
                .andReturn();

        /*
         * 로그인 성공 후 session을 얻는다.
         *
         * ChangeSessionIdAuthenticationStrategy 때문에
         * id는 바뀌어도 session 자체는 이어진다.
         */
        MockHttpSession authenticatedSession =
            (MockHttpSession)
                loginResult
                    .getRequest()
                    .getSession(false);

        assertThat(
            authenticatedSession
        ).isNotNull();

        /*
         * 3.
         * 로그인 성공 후 다시 /api/csrf 호출
         *
         * CsrfAuthenticationStrategy가
         * 기존 token을 제거했기 때문에
         * 새로운 CSRF token이 생성되어야 한다.
         */
        CsrfCredentials afterLogin =
            issueCsrf(
                authenticatedSession
            );

        assertThat(
            afterLogin.token()
        ).isNotBlank();

        /*
         * 4.
         * 로그인 전 token과
         * 로그인 후 token은 달라야 한다.
         */
        assertThat(
            afterLogin.token()
        ).isNotEqualTo(
            beforeLogin.token()
        );
    }

    /*
     * 실제 CookieCsrfTokenRepository 흐름을 이용해서
     * CSRF credential을 발급받는 helper.
     *
     * 절대 with(csrf())를 사용하지 않는다.
     */
    private CsrfCredentials issueCsrf(
        MockHttpSession session
    ) throws Exception {

        MvcResult result =
            mockMvc.perform(
                    get("/api/csrf")
                        .session(
                            session
                        )
                )
                .andExpect(
                    status().isOk()
                )
                .andExpect(
                    jsonPath("$.headerName")
                        .value(
                            "X-XSRF-TOKEN"
                        )
                )
                .andReturn();

        Cookie cookie =
            result
                .getResponse()
                .getCookie(
                    "XSRF-TOKEN"
                );

        assertThat(
            cookie
        ).isNotNull();

        assertThat(
            cookie.getValue()
        ).isNotBlank();

        return new CsrfCredentials(
            cookie,
            cookie.getValue()
        );
    }

    /*
     * 실제 브라우저가 CSRF 요청에 필요한 두 값을 묶는다.
     *
     * cookie:
     * XSRF-TOKEN=...
     *
     * header:
     * X-XSRF-TOKEN: ...
     */
    private record CsrfCredentials(
        Cookie cookie,
        String token
    ) {
    }
}