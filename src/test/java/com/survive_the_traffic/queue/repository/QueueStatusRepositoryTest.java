package com.survive_the_traffic.queue.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.survive_the_traffic.queue.entity.QueueStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
class QueueStatusRepositoryTest {

    private static final Long CONCERT_ID = 1L;

    @Autowired
    private QueueStatusRepository queueStatusRepository;

    @Autowired
    private WaitingQueueRepository waitingQueueRepository;

    @Autowired
    private QueueAdmissionRepository queueAdmissionRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void tearDown() {

        redisTemplate.delete(
            "queue:waiting:" + CONCERT_ID
        );

        redisTemplate.delete(
            "queue:sequence:" + CONCERT_ID
        );

        redisTemplate.delete(
            "queue:admitted:" + CONCERT_ID
        );

        redisTemplate.delete(
            "queue:active-concerts"
        );
    }

    @Test
    void waiting_사용자의_상태를_조회한다() {

        // given
        waitingQueueRepository.join(
            CONCERT_ID,
            10L
        );

        waitingQueueRepository.join(
            CONCERT_ID,
            20L
        );

        // when
        QueueStatusResult result =
            queueStatusRepository.getStatus(
                CONCERT_ID,
                20L
            );

        // then
        assertThat(result.status())
            .isEqualTo(QueueStatus.WAITING);

        assertThat(result.rank())
            .isEqualTo(1L);

        assertThat(result.waitingCount())
            .isEqualTo(2L);
    }

    @Test
    void admitted_사용자의_상태를_조회한다() {

        // given
        waitingQueueRepository.join(
            CONCERT_ID,
            10L
        );

        waitingQueueRepository.join(
            CONCERT_ID,
            20L
        );

        queueAdmissionRepository.admit(
            CONCERT_ID,
            1L
        );

        // when
        QueueStatusResult result =
            queueStatusRepository.getStatus(
                CONCERT_ID,
                10L
            );

        // then
        assertThat(result.status())
            .isEqualTo(QueueStatus.ADMITTED);

        assertThat(result.rank())
            .isNull();

        assertThat(result.waitingCount())
            .isEqualTo(1L);
    }

    @Test
    void queue에_없는_사용자는_NOT_JOINED이다() {

        // given
        waitingQueueRepository.join(
            CONCERT_ID,
            10L
        );

        // when
        QueueStatusResult result =
            queueStatusRepository.getStatus(
                CONCERT_ID,
                999L
            );

        // then
        assertThat(result.status())
            .isEqualTo(QueueStatus.NOT_JOINED);

        assertThat(result.rank())
            .isNull();

        assertThat(result.waitingCount())
            .isEqualTo(1L);
    }

    @Test
    void 만료된_admission은_ADMITTED로_판단하지_않는다() {

        // given
        redisTemplate.opsForZSet()
            .add(
                "queue:admitted:" + CONCERT_ID,
                "10",
                System.currentTimeMillis() - 1000
            );

        // when
        QueueStatusResult result =
            queueStatusRepository.getStatus(
                CONCERT_ID,
                10L
            );

        // then
        assertThat(result.status())
            .isEqualTo(QueueStatus.NOT_JOINED);

        assertThat(result.rank())
            .isNull();

        assertThat(result.waitingCount())
            .isZero();
    }

    @Test
    void admission은_만료됐지만_waiting에_있으면_WAITING으로_판단한다() {

        // given
        waitingQueueRepository.join(
            CONCERT_ID,
            10L
        );

        redisTemplate.opsForZSet()
            .add(
                "queue:admitted:" + CONCERT_ID,
                "10",
                System.currentTimeMillis() - 1000
            );

        // when
        QueueStatusResult result =
            queueStatusRepository.getStatus(
                CONCERT_ID,
                10L
            );

        // then
        assertThat(result.status())
            .isEqualTo(QueueStatus.WAITING);

        assertThat(result.rank())
            .isEqualTo(0L);

        assertThat(result.waitingCount())
            .isEqualTo(1L);
    }
}