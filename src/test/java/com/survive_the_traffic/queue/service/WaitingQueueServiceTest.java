package com.survive_the_traffic.queue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.survive_the_traffic.queue.dto.WaitingQueueJoinResult;
import com.survive_the_traffic.queue.entity.QueueStatus;
import com.survive_the_traffic.queue.dto.QueueStatusResponse;
import com.survive_the_traffic.queue.dto.WaitingQueueResponse;
import com.survive_the_traffic.queue.repository.QueueStatusRepository;
import com.survive_the_traffic.queue.repository.QueueStatusResult;
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
    QueueStatusRepository queueStatusRepository;

    @InjectMocks
    WaitingQueueService waitingQueueService;

    @Test
    void redis_rank를_사용자_position으로_변환한다() {

        // given
        Long concertId = 1L;
        Long userId = 20L;

        given(
            waitingQueueRepository.join(
                concertId,
                userId
            )
        ).willReturn(
            new WaitingQueueJoinResult(
                true,
                1L,
                3L
            )
        );

        // when
        WaitingQueueResponse response =
            waitingQueueService.join(
                concertId,
                userId
            );

        // then
        assertThat(response.concertId())
            .isEqualTo(concertId);

        assertThat(response.userId())
            .isEqualTo(userId);

        assertThat(response.position())
            .isEqualTo(2L);

        assertThat(response.waitingCount())
            .isEqualTo(3L);

        then(waitingQueueRepository)
            .should()
            .join(concertId, userId);

        /*
         * join 이후 다시 Redis 상태를 조회하면
         * Worker와 race가 생길 수 있으므로 호출하면 안 된다.
         */
        then(waitingQueueRepository)
            .should(never())
            .getRank(
                anyLong(),
                anyLong()
            );

        then(waitingQueueRepository)
            .should(never())
            .getSize(
                anyLong()
            );
    }

    @Test
    void waiting_사용자의_상태를_조회한다() {

        // given
        given(
            queueStatusRepository.getStatus(
                1L,
                10L
            )
        ).willReturn(
            new QueueStatusResult(
                QueueStatus.WAITING,
                4L,
                100L
            )
        );

        // when
        QueueStatusResponse response =
            waitingQueueService.getStatus(
                1L,
                10L
            );

        // then
        assertThat(response.status())
            .isEqualTo(QueueStatus.WAITING);

        /*
         * Redis rank = 4
         * 사용자 position = 5
         */
        assertThat(response.position())
            .isEqualTo(5L);

        assertThat(response.waitingCount())
            .isEqualTo(100L);

        then(queueStatusRepository)
            .should()
            .getStatus(1L, 10L);
    }

    @Test
    void admitted_사용자의_상태를_조회한다() {

        // given
        given(
            queueStatusRepository.getStatus(
                1L,
                10L
            )
        ).willReturn(
            new QueueStatusResult(
                QueueStatus.ADMITTED,
                null,
                50L
            )
        );

        // when
        QueueStatusResponse response =
            waitingQueueService.getStatus(
                1L,
                10L
            );

        // then
        assertThat(response.status())
            .isEqualTo(QueueStatus.ADMITTED);

        assertThat(response.position())
            .isNull();

        assertThat(response.waitingCount())
            .isEqualTo(50L);
    }

    @Test
    void queue에_없는_사용자의_상태를_조회한다() {

        // given
        given(
            queueStatusRepository.getStatus(
                1L,
                10L
            )
        ).willReturn(
            new QueueStatusResult(
                QueueStatus.NOT_JOINED,
                null,
                50L
            )
        );

        // when
        QueueStatusResponse response =
            waitingQueueService.getStatus(
                1L,
                10L
            );

        // then
        assertThat(response.status())
            .isEqualTo(QueueStatus.NOT_JOINED);

        assertThat(response.position())
            .isNull();

        assertThat(response.waitingCount())
            .isEqualTo(50L);
    }
}