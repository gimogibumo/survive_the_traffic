package com.survive_the_traffic.auth.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.survive_the_traffic.auth.dto.SignupRequest;
import com.survive_the_traffic.auth.dto.SignupResponse;
import com.survive_the_traffic.auth.exception.DuplicateEmailException;
import com.survive_the_traffic.auth.service.AuthService;
import com.survive_the_traffic.common.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @Test
    void 회원가입에_성공하면_201을_반환한다()
        throws Exception {

        // given
        SignupRequest request =
            new SignupRequest(
                "kim@example.com",
                "password1234",
                "기범"
            );

        SignupResponse response =
            new SignupResponse(
                1L,
                "kim@example.com",
                "기범"
            );

        given(
            authService.signup(request)
        ).willReturn(response);

        // when / then
        mockMvc.perform(
                post("/api/auth/signup")
                    .with(csrf())
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        objectMapper.writeValueAsString(
                            request
                        )
                    )
            )
            .andExpect(
                status().isCreated()
            )
            .andExpect(
                jsonPath("$.id")
                    .value(1L)
            )
            .andExpect(
                jsonPath("$.email")
                    .value("kim@example.com")
            )
            .andExpect(
                jsonPath("$.name")
                    .value("기범")
            )
            .andExpect(
                jsonPath("$.password")
                    .doesNotExist()
            )
            .andExpect(
                jsonPath("$.passwordHash")
                    .doesNotExist()
            );
    }

    @Test
    void 중복된_이메일이면_409를_반환한다()
        throws Exception {

        // given
        SignupRequest request =
            new SignupRequest(
                "kim@example.com",
                "password1234",
                "기범"
            );

        given(
            authService.signup(request)
        ).willThrow(
            new DuplicateEmailException()
        );

        // when / then
        mockMvc.perform(
                post("/api/auth/signup")
                    .with(csrf())
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        objectMapper.writeValueAsString(
                            request
                        )
                    )
            )
            .andExpect(
                status().isConflict()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("DUPLICATE_EMAIL")
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "이미 사용 중인 이메일입니다."
                    )
            );
    }

    @Test
    void 이메일_형식이_잘못되면_400을_반환한다()
        throws Exception {

        String request = """
            {
                "email": "not-an-email",
                "password": "password1234",
                "name": "기범"
            }
            """;

        mockMvc.perform(
                post("/api/auth/signup")
                    .with(csrf())
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(request)
            )
            .andExpect(
                status().isBadRequest()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("INVALID_REQUEST")
            )
            .andExpect(
                jsonPath("$.message")
                    .value("잘못된 요청입니다.")
            );
    }

    @Test
    void 비밀번호가_8자보다_짧으면_400을_반환한다()
        throws Exception {

        String request = """
            {
                "email": "kim@example.com",
                "password": "1234567",
                "name": "기범"
            }
            """;

        mockMvc.perform(
                post("/api/auth/signup")
                    .with(csrf())
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(request)
            )
            .andExpect(
                status().isBadRequest()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("INVALID_REQUEST")
            );
    }

    @Test
    void 이름이_비어있으면_400을_반환한다()
        throws Exception {

        String request = """
            {
                "email": "kim@example.com",
                "password": "password1234",
                "name": ""
            }
            """;

        mockMvc.perform(
                post("/api/auth/signup")
                    .with(csrf())
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(request)
            )
            .andExpect(
                status().isBadRequest()
            )
            .andExpect(
                jsonPath("$.code")
                    .value("INVALID_REQUEST")
            );
    }
}