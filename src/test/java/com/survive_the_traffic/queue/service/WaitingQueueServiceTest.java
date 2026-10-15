package com.survive_the_traffic.queue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.survive_the_traffic.queue.dto.WaitingQueueResponse;
import com.survive_the_traffic.queue.repository.WaitingQueueRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WaitingQueueServiceTest {

    @Mock
    WaitingQueueRepository waitingQueueRepository;

    @InjectMocks
    WaitingQueueService waitingQueueService;

    @Test
    void redis_rank를_사용자_position으로_변환한다() {
        Long concertId = 1L;
        Long userId = 20L;

        when(waitingQueueRepository.join(concertId,userId)).thenReturn(true);
        when(waitingQueueRepository.getRank(concertId, userId))
            .thenReturn(1L);
        when(waitingQueueRepository.getSize(concertId))
            .thenReturn(3L);

        WaitingQueueResponse response =
            waitingQueueService.join(concertId, userId);

        assertThat(response.position()).isEqualTo(2L);
        assertThat(response.waitingCount()).isEqualTo(3L);
    }
}