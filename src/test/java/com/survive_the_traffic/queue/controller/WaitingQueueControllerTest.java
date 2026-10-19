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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(WaitingQueueController.class)
@Import(SecurityConfig.class)
class WaitingQueueControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    WaitingQueueService waitingQueueService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void 대기열에_진입한다() throws Exception {
        WaitingQueueResponse response =
            new WaitingQueueResponse(
                1L, 10L, 3L, 100L
            );

        given(waitingQueueService.join(1L,10L))
            .willReturn(response);

        mockMvc.perform(
            post("/api/queues/1/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "userId": 10
                    }
                    """)
                .with(csrf())
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.concertId").value(1))
            .andExpect(jsonPath("$.userId").value(10))
            .andExpect(jsonPath("$.position").value(3))
            .andExpect(jsonPath("$.waitingCount").value(100));
        then(waitingQueueService).should().join(1L, 10L);
    }

    @Test
    void 현재_대기순서를_조회한다() throws Exception {

        WaitingQueueResponse response =
            new WaitingQueueResponse(
                1L,
                20L,
                2L,
                3L
            );

        given(waitingQueueService.getPosition(1L, 20L))
            .willReturn(response);

        mockMvc.perform(
                get("/api/queues/1/position")
                    .param("userId", "20")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.position").value(2))
            .andExpect(jsonPath("$.waitingCount").value(3));
    }

    @Test
    void queue_상태를_조회한다() throws Exception {

        // given
        QueueStatusResponse response =
            new QueueStatusResponse(
                1L,
                10L,
                QueueStatus.WAITING,
                5L,
                100L
            );

        given(
            waitingQueueService.getStatus(1L, 10L)
        ).willReturn(response);

        // when & then
        mockMvc.perform(
                get("/api/queues/1/status")
                    .param("userId", "10")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.concertId").value(1))
            .andExpect(jsonPath("$.userId").value(10))
            .andExpect(jsonPath("$.status").value("WAITING"))
            .andExpect(jsonPath("$.position").value(5))
            .andExpect(jsonPath("$.waitingCount").value(100));
    }

    @Test
    void admitted_사용자는_position이_null이다() throws Exception {

        QueueStatusResponse response =
            new QueueStatusResponse(
                1L,
                10L,
                QueueStatus.ADMITTED,
                null,
                50L
            );

        given(
            waitingQueueService.getStatus(1L, 10L)
        ).willReturn(response);

        mockMvc.perform(
                get("/api/queues/1/status")
                    .param("userId", "10")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ADMITTED"))
            .andExpect(jsonPath("$.position").isEmpty())
            .andExpect(jsonPath("$.waitingCount").value(50));
    }
}