package com.survive_the_traffic.queue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.survive_the_traffic.queue.dto.QueueStatusResponse;
import com.survive_the_traffic.queue.dto.WaitingQueueResponse;
import com.survive_the_traffic.queue.entitiy.QueueStatus;
import com.survive_the_traffic.queue.repository.ActiveConcertRepository;
import com.survive_the_traffic.queue.repository.AdmittedQueueRepository;
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

    @Mock
    AdmittedQueueRepository admittedQueueRepository;

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

    @Test
    void 대기열에_join하면_concert를_active로_등록한다() {

        // given
        Long concertId = 1L;
        Long userId = 10L;

        given(
            waitingQueueRepository.join(
                concertId,
                userId
            )
        ).willReturn(true);

        given(
            waitingQueueRepository.getRank(
                concertId,
                userId
            )
        ).willReturn(0L);

        given(
            waitingQueueRepository.getSize(
                concertId
            )
        ).willReturn(1L);

        // when
        WaitingQueueResponse response =
            waitingQueueService.join(
                concertId,
                userId
            );

        // then

        assertThat(response.position())
            .isEqualTo(1L);

        assertThat(response.waitingCount())
            .isEqualTo(1L);

    }

    @Test
    void 이미_waiting인_사용자가_다시_join해도_concert는_activate한다() {

        // given
        Long concertId = 1L;
        Long userId = 10L;

        given(
            waitingQueueRepository.join(
                concertId,
                userId
            )
        ).willReturn(false);

        given(
            waitingQueueRepository.getRank(
                concertId,
                userId
            )
        ).willReturn(2L);

        given(
            waitingQueueRepository.getSize(
                concertId
            )
        ).willReturn(10L);

        // when
        WaitingQueueResponse response =
            waitingQueueService.join(
                concertId,
                userId
            );

        // then
        assertThat(response.position())
            .isEqualTo(3L);

        assertThat(response.waitingCount())
            .isEqualTo(10L);
    }

    @Test
    void waiting_사용자의_상태를_조회한다() {

        // given
        given(
            admittedQueueRepository.isAdmitted(1L, 10L)
        ).willReturn(false);

        given(
            waitingQueueRepository.getRank(1L, 10L)
        ).willReturn(4L);

        given(
            waitingQueueRepository.getSize(1L)
        ).willReturn(100L);

        // when
        QueueStatusResponse response =
            waitingQueueService.getStatus(1L, 10L);

        // then
        assertThat(response.status())
            .isEqualTo(QueueStatus.WAITING);

        assertThat(response.position())
            .isEqualTo(5L);

        assertThat(response.waitingCount())
            .isEqualTo(100L);
    }

    @Test
    void admitted_사용자의_상태를_조회한다() {

        // given
        given(
            admittedQueueRepository.isAdmitted(1L, 10L)
        ).willReturn(true);

        given(
            waitingQueueRepository.getSize(1L)
        ).willReturn(50L);

        // when
        QueueStatusResponse response =
            waitingQueueService.getStatus(1L, 10L);

        // then
        assertThat(response.status())
            .isEqualTo(QueueStatus.ADMITTED);

        assertThat(response.position())
            .isNull();

        then(waitingQueueRepository)
            .should(never())
            .getRank(1L, 10L);
    }

    @Test
    void queue에_없는_사용자의_상태를_조회한다() {

        // given
        given(
            admittedQueueRepository.isAdmitted(1L, 10L)
        ).willReturn(false);

        given(
            waitingQueueRepository.getRank(1L, 10L)
        ).willReturn(null);

        given(
            waitingQueueRepository.getSize(1L)
        ).willReturn(50L);

        // when
        QueueStatusResponse response =
            waitingQueueService.getStatus(1L, 10L);

        // then
        assertThat(response.status())
            .isEqualTo(QueueStatus.NOT_JOINED);

        assertThat(response.position())
            .isNull();
    }

    
}