package com.survive_the_traffic.queue.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

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
        redisTemplate.delete("queue:waiting:" + CONCERT_ID);
        redisTemplate.delete("queue:sequence:" + CONCERT_ID);
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

}