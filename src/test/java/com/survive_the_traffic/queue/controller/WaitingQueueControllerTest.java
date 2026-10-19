package com.survive_the_traffic.queue.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.survive_the_traffic.common.config.SecurityConfig;
import com.survive_the_traffic.queue.dto.QueueStatusResponse;
import com.survive_the_traffic.queue.dto.WaitingQueueResponse;
import com.survive_the_traffic.queue.entity.QueueStatus;
import com.survive_the_traffic.queue.service.WaitingQueueService;
import com.survive_the_traffic.user.domain.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import com.survive_the_traffic.auth.security.AuthenticatedUser;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@WebMvcTest(WaitingQueueController.class)
@Import(SecurityConfig.class)
class WaitingQueueControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    WaitingQueueService waitingQueueService;

    @Autowired
    ObjectMapper objectMapper;

    private static final Long USER_ID = 10L;
    private static final Long CONCERT_ID = 1L;

    @Test
    void 대기열에_진입한다() throws Exception {

        WaitingQueueResponse response =
            new WaitingQueueResponse(
                CONCERT_ID,
                USER_ID,
                3L,
                100L
            );

        given(
            waitingQueueService.join(
                CONCERT_ID,
                USER_ID
            )
        ).willReturn(response);

        mockMvc.perform(
                post("/api/queues/{concertId}/join", CONCERT_ID)
                    .with(user(authenticatedUser()))
                    .with(csrf())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.concertId").value(CONCERT_ID))
            .andExpect(jsonPath("$.userId").value(USER_ID))
            .andExpect(jsonPath("$.position").value(3))
            .andExpect(jsonPath("$.waitingCount").value(100));

        then(waitingQueueService)
            .should()
            .join(
                CONCERT_ID,
                USER_ID
            );
    }

    @Test
    void 현재_대기순서를_조회한다() throws Exception {

        WaitingQueueResponse response =
            new WaitingQueueResponse(
                CONCERT_ID,
                USER_ID,
                2L,
                3L
            );

        given(
            waitingQueueService.getPosition(
                CONCERT_ID,
                USER_ID
            )
        ).willReturn(response);

        mockMvc.perform(
                get("/api/queues/{concertId}/position", CONCERT_ID)
                    .with(user(authenticatedUser()))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.position").value(2))
            .andExpect(jsonPath("$.waitingCount").value(3));

        then(waitingQueueService)
            .should()
            .getPosition(
                CONCERT_ID,
                USER_ID
            );
    }

    @Test
    void queue_상태를_조회한다() throws Exception {

        QueueStatusResponse response =
            new QueueStatusResponse(
                CONCERT_ID,
                USER_ID,
                QueueStatus.WAITING,
                5L,
                100L
            );

        given(
            waitingQueueService.getStatus(
                CONCERT_ID,
                USER_ID
            )
        ).willReturn(response);

        mockMvc.perform(
                get("/api/queues/{concertId}/status", CONCERT_ID)
                    .with(user(authenticatedUser()))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.concertId").value(CONCERT_ID))
            .andExpect(jsonPath("$.userId").value(USER_ID))
            .andExpect(jsonPath("$.status").value("WAITING"))
            .andExpect(jsonPath("$.position").value(5))
            .andExpect(jsonPath("$.waitingCount").value(100));

        then(waitingQueueService)
            .should()
            .getStatus(
                CONCERT_ID,
                USER_ID
            );
    }

    @Test
    void admitted_사용자는_position이_null이다() throws Exception {

        QueueStatusResponse response =
            new QueueStatusResponse(
                CONCERT_ID,
                USER_ID,
                QueueStatus.ADMITTED,
                null,
                50L
            );

        given(
            waitingQueueService.getStatus(
                CONCERT_ID,
                USER_ID
            )
        ).willReturn(response);

        mockMvc.perform(
                get("/api/queues/{concertId}/status", CONCERT_ID)
                    .with(user(authenticatedUser()))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ADMITTED"))
            .andExpect(jsonPath("$.position").isEmpty())
            .andExpect(jsonPath("$.waitingCount").value(50));
    }

    @Test
    void 인증되지_않은_사용자는_queue_상태를_조회할_수_없다() throws Exception {

        mockMvc.perform(
                get("/api/queues/{concertId}/status", CONCERT_ID)
            )
            .andExpect(status().isUnauthorized());
    }

    @Test
    void CSRF_토큰이_없으면_대기열에_진입할_수_없다() throws Exception {

        mockMvc.perform(
                post("/api/queues/{concertId}/join", CONCERT_ID)
                    .with(user(authenticatedUser()))
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void query_parameter의_userId가_아니라_인증된_사용자_id를_사용한다()
        throws Exception {

        QueueStatusResponse response =
            new QueueStatusResponse(
                CONCERT_ID,
                USER_ID,
                QueueStatus.WAITING,
                5L,
                100L
            );

        given(
            waitingQueueService.getStatus(
                CONCERT_ID,
                USER_ID
            )
        ).willReturn(response);

        mockMvc.perform(
                get("/api/queues/{concertId}/status", CONCERT_ID)
                    .param("userId", "999")
                    .with(user(authenticatedUser()))
            )
            .andExpect(status().isOk());

        then(
            waitingQueueService
        )
            .should()
            .getStatus(
                CONCERT_ID,
                USER_ID
            );
    }

    private AuthenticatedUser authenticatedUser() {

        User user = User.builder()
            .id(USER_ID)
            .email("kim@example.com")
            .name("기범")
            .passwordHash("encoded-password")
            .createdAt(LocalDateTime.of(2026, 10, 19, 10, 0))
            .build();

        return AuthenticatedUser.from(user);
    }
}