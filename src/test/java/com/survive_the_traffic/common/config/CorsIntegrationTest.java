package com.survive_the_traffic.common.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CorsIntegrationTest {

    private static final String ALLOWED_ORIGIN =
        "http://localhost:5173";

    private static final String DISALLOWED_ORIGIN =
        "http://evil.example";

    @Autowired
    private MockMvc mockMvc;


    @Test
    void 허용된_origin의_preflight_요청은_통과한다()
        throws Exception {

        mockMvc.perform(
                options(
                    "/api/auth/login"
                )
                    .header(
                        HttpHeaders.ORIGIN,
                        ALLOWED_ORIGIN
                    )
                    .header(
                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                        "POST"
                    )
                    .header(
                        HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                        "Content-Type"
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                header().string(
                    HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                    ALLOWED_ORIGIN
                )
            )
            .andExpect(
                header().string(
                    HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                    "true"
                )
            );
    }


    @Test
    void 허용된_origin은_X_XSRF_TOKEN_header를_사용할_수_있다()
        throws Exception {

        mockMvc.perform(
                options(
                    "/api/reservations"
                )
                    .header(
                        HttpHeaders.ORIGIN,
                        ALLOWED_ORIGIN
                    )
                    .header(
                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                        "POST"
                    )
                    .header(
                        HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                        "Content-Type, X-XSRF-TOKEN"
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                header().string(
                    HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                    ALLOWED_ORIGIN
                )
            )
            .andExpect(
                header().string(
                    HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                    "true"
                )
            )
            .andExpect(
                header().string(
                    HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                    org.hamcrest.Matchers.containsStringIgnoringCase(
                        "X-XSRF-TOKEN"
                    )
                )
            );
    }


    @Test
    void 허용된_origin의_실제요청에도_CORS_header가_포함된다()
        throws Exception {

        mockMvc.perform(
                get(
                    "/api/csrf"
                )
                    .header(
                        HttpHeaders.ORIGIN,
                        ALLOWED_ORIGIN
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                header().string(
                    HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                    ALLOWED_ORIGIN
                )
            )
            .andExpect(
                header().string(
                    HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                    "true"
                )
            );
    }


    @Test
    void 허용되지_않은_origin의_preflight_요청은_차단된다()
        throws Exception {

        mockMvc.perform(
                options(
                    "/api/auth/login"
                )
                    .header(
                        HttpHeaders.ORIGIN,
                        DISALLOWED_ORIGIN
                    )
                    .header(
                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                        "POST"
                    )
            )
            .andExpect(
                status().isForbidden()
            )
            .andExpect(
                header().doesNotExist(
                    HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                )
            );
    }
}