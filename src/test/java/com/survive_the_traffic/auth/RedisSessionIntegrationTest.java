package com.survive_the_traffic.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.survive_the_traffic.auth.security.AuthenticatedUser;
import com.survive_the_traffic.auth.security.DatabaseUserDetailsService;
import com.survive_the_traffic.user.domain.User;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.session.data.redis.namespace=ci:redis-session-test"
})
@SuppressWarnings({"rawtypes", "unchecked"})
class RedisSessionIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private SessionRepository sessions;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private DatabaseUserDetailsService userDetailsService;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void redis_쿠키로_인증을_복원하고_세션고정과_로그아웃을_검증한다() throws Exception {
        given(userDetailsService.loadUserByUsername("redis-session@example.com"))
            .willReturn(AuthenticatedUser.from(User.builder()
                .id(1L).email("redis-session@example.com").name("Redis session test")
                .passwordHash(passwordEncoder.encode("password1234"))
                .createdAt(LocalDateTime.now()).build()));

        Session initial = sessions.createSession();
        initial.setAttribute("beforeLogin", true);
        sessions.save(initial);
        String initialCookie = "SESSION=" + Base64.getEncoder()
            .encodeToString(initial.getId().getBytes(StandardCharsets.UTF_8));
        String authenticatedId = null;
        try {
            HttpResponse<String> csrf = request("GET", "/api/csrf", initialCookie, null, null);
            assertThat(csrf.statusCode()).isEqualTo(200);
            String xsrfCookie = cookie(csrf, "XSRF-TOKEN");
            HttpResponse<String> login = request("POST", "/api/auth/login",
                initialCookie + "; " + xsrfCookie, value(xsrfCookie),
                """
                {"email":"redis-session@example.com","password":"password1234"}
                """);
            assertThat(login.statusCode()).isEqualTo(200);
            String authenticatedCookie = cookie(login, "SESSION");
            assertThat(authenticatedCookie).isNotEqualTo(initialCookie);
            authenticatedId = new String(Base64.getDecoder().decode(value(authenticatedCookie)),
                StandardCharsets.UTF_8);
            assertThat(sessions.findById(initial.getId())).isNull();
            assertThat(sessions.findById(authenticatedId)).isNotNull();
            assertThat(request("GET", "/api/users/me", authenticatedCookie, null, null)
                .statusCode()).isEqualTo(200);
            assertThat(request("GET", "/api/users/me", initialCookie, null, null)
                .statusCode()).isEqualTo(401);

            // CSRF 없는 로그아웃은 거부되고 인증 세션은 유지된다.
            assertThat(request("POST", "/api/auth/logout", authenticatedCookie, null, "")
                .statusCode()).isEqualTo(403);
            assertThat(request("GET", "/api/users/me", authenticatedCookie, null, null)
                .statusCode()).isEqualTo(200);
            csrf = request("GET", "/api/csrf", authenticatedCookie, null, null);
            xsrfCookie = cookie(csrf, "XSRF-TOKEN");
            assertThat(request("POST", "/api/auth/logout",
                authenticatedCookie + "; " + xsrfCookie, value(xsrfCookie), "")
                .statusCode()).isEqualTo(204);
            assertThat(sessions.findById(authenticatedId)).isNull();
            assertThat(request("GET", "/api/users/me", authenticatedCookie, null, null)
                .statusCode()).isEqualTo(401);
        } finally {
            sessions.deleteById(initial.getId());
            if (authenticatedId != null) {
                sessions.deleteById(authenticatedId);
            }
        }
    }

    private HttpResponse<String> request(String method, String path, String cookies,
        String csrf, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
            URI.create("http://localhost:" + port + path))
            .header("Cookie", cookies).header("Content-Type", "application/json");
        if (csrf != null) {
            builder.header("X-XSRF-TOKEN", csrf);
        }
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String cookie(HttpResponse<?> response, String name) {
        return response.headers().allValues("set-cookie").stream()
            .map(header -> header.split(";", 2)[0])
            .filter(header -> header.startsWith(name + "="))
            .findFirst().orElseThrow(() -> new AssertionError("Missing cookie: " + name));
    }

    private String value(String cookie) {
        return cookie.substring(cookie.indexOf('=') + 1);
    }
}
