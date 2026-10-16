package com.survive_the_traffic.queue.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
class WaitingQueueRepositoryTest {

    @Autowired
    private WaitingQueueRepository waitingQueueRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final Long CONCERT_ID = 1L;

    @AfterEach
    void tearDown() {
        redisTemplate.delete(
            "queue:waiting:" + CONCERT_ID
        );

        redisTemplate.delete(
            "queue:sequence:" + CONCERT_ID
        );

        redisTemplate.delete(
            "queue:active-concerts"
        );
    }

    @Test
    void 대기열에_들어온_순서대로_rank가_부여된다() {
        //given & when
        boolean first = waitingQueueRepository.join(CONCERT_ID, 10L);
        boolean second = waitingQueueRepository.join(CONCERT_ID, 20L);
        boolean third = waitingQueueRepository.join(CONCERT_ID, 30L);

        //then
        assertThat(waitingQueueRepository.getRank(CONCERT_ID,10L))
            .isEqualTo(0L);
        assertThat(waitingQueueRepository.getRank(CONCERT_ID, 20L))
            .isEqualTo(1L);

        assertThat(waitingQueueRepository.getRank(CONCERT_ID, 30L))
            .isEqualTo(2L);

        assertThat(waitingQueueRepository.getSize(CONCERT_ID))
            .isEqualTo(3L);
    }

    @Test
    void 같은_사용자가_다시_join해도_대기열_순서는_변하지_않는다() {
        //given
        waitingQueueRepository.join(CONCERT_ID, 10L);
        waitingQueueRepository.join(CONCERT_ID, 20L);
        waitingQueueRepository.join(CONCERT_ID, 30L);

        //when
        boolean added = waitingQueueRepository.join(CONCERT_ID, 20L);

        //then
        assertThat(added).isFalse();
        assertThat(waitingQueueRepository.getRank(CONCERT_ID,20L))
            .isEqualTo(1L);
        assertThat(waitingQueueRepository.getSize(CONCERT_ID))
            .isEqualTo(3L);
    }

    @Test
    void 앞에서부터_N명의_사용자를_꺼낸다() {

        // given
        waitingQueueRepository.join(CONCERT_ID, 10L);
        waitingQueueRepository.join(CONCERT_ID, 20L);
        waitingQueueRepository.join(CONCERT_ID, 30L);
        waitingQueueRepository.join(CONCERT_ID, 40L);
        waitingQueueRepository.join(CONCERT_ID, 50L);

        // when
        List<Long> popped =
            waitingQueueRepository.popFirst(CONCERT_ID, 3L);

        // then
        assertThat(popped)
            .containsExactly(10L, 20L, 30L);

        assertThat(
            waitingQueueRepository.getSize(CONCERT_ID)
        ).isEqualTo(2L);

        assertThat(
            waitingQueueRepository.getRank(CONCERT_ID, 40L)
        ).isEqualTo(0L);

        assertThat(
            waitingQueueRepository.getRank(CONCERT_ID, 50L)
        ).isEqualTo(1L);
    }

    @Test
    void 중복_join은_sequence를_추가로_소비하지_않는다() {

        // given
        boolean first =
            waitingQueueRepository.join(
                CONCERT_ID,
                10L
            );

        // when
        boolean second =
            waitingQueueRepository.join(
                CONCERT_ID,
                10L
            );

        // then
        assertThat(first).isTrue();
        assertThat(second).isFalse();

        String sequence =
            redisTemplate.opsForValue()
                .get(
                    "queue:sequence:" + CONCERT_ID
                );

        assertThat(sequence)
            .isEqualTo("1");

        assertThat(
            waitingQueueRepository.getRank(
                CONCERT_ID,
                10L
            )
        ).isEqualTo(0L);

        assertThat(
            waitingQueueRepository.getSize(
                CONCERT_ID
            )
        ).isEqualTo(1L);
    }

    @Test
    void 중복_join은_누락된_active_concert를_복구한다() {

        // given
        waitingQueueRepository.join(
            CONCERT_ID,
            10L
        );

        redisTemplate.opsForSet()
            .remove(
                "queue:active-concerts",
                CONCERT_ID.toString()
            );

        Boolean before =
            redisTemplate.opsForSet()
                .isMember(
                    "queue:active-concerts",
                    CONCERT_ID.toString()
                );

        assertThat(before).isFalse();

        // when
        boolean added =
            waitingQueueRepository.join(
                CONCERT_ID,
                10L
            );

        // then
        assertThat(added).isFalse();

        Boolean after =
            redisTemplate.opsForSet()
                .isMember(
                    "queue:active-concerts",
                    CONCERT_ID.toString()
                );

        assertThat(after).isTrue();

        assertThat(
            redisTemplate.opsForValue()
                .get("queue:sequence:" + CONCERT_ID)
        ).isEqualTo("1");
    }
}