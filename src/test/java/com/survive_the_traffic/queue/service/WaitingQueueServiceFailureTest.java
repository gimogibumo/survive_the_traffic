package com.survive_the_traffic.queue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.willThrow;

import com.survive_the_traffic.queue.repository.ActiveConcertRepository;
import com.survive_the_traffic.queue.repository.WaitingQueueRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
public class WaitingQueueServiceFailureTest {

    private static final Long CONCERT_ID = 1L;
    private static final Long USER_ID = 10L;

    @Autowired
    private WaitingQueueService waitingQueueService;

    @Autowired
    private WaitingQueueRepository waitingQueueRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private ActiveConcertRepository activeConcertRepository;

    @AfterEach
    void tearDown() {
        redisTemplate.delete("queue:waiting:" + CONCERT_ID);
        redisTemplate.delete("queue:sequence:" + CONCERT_ID);
        redisTemplate.delete("queue:active-concerts");
    }

    @Test
    void waiting_join_후_active_등록에_실패하면_orphan_queue가_생긴다() {

        // given
        willThrow(new RuntimeException("active concert 등록 실패"))
            .given(activeConcertRepository)
            .activate(CONCERT_ID);

        // when & then
        assertThatThrownBy(
            () -> waitingQueueService.join(
                CONCERT_ID,
                USER_ID
            )
        )
            .isInstanceOf(RuntimeException.class);

        // waiting에는 이미 들어가 버림
        assertThat(
            waitingQueueRepository.getSize(CONCERT_ID)
        ).isEqualTo(1L);

        assertThat(
            waitingQueueRepository.getRank(
                CONCERT_ID,
                USER_ID
            )
        ).isEqualTo(0L);

        // 하지만 active registry에는 없음
        Boolean active =
            redisTemplate.opsForSet()
                .isMember(
                    "queue:active-concerts",
                    CONCERT_ID.toString()
                );

        assertThat(active).isFalse();
    }
}
