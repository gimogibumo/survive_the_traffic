package com.survive_the_traffic.queue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;

import com.survive_the_traffic.concert.domain.Concert;
import com.survive_the_traffic.queue.repository.AdmittedQueueRepository;
import com.survive_the_traffic.queue.repository.WaitingQueueRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class QueueAdmissionServiceFailureTest {

    private static final Long CONCERT_ID = 1L;

    @Autowired
    private QueueAdmissionService queueAdmissionService;

    @Autowired
    private WaitingQueueRepository waitingQueueRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private AdmittedQueueRepository admittedQueueRepository;

    @AfterEach
    void tearDown() {
        redisTemplate.delete("queue:waiting:" + CONCERT_ID);
        redisTemplate.delete("queue:admitted:" + CONCERT_ID);
        redisTemplate.delete("queue:sequence:" + CONCERT_ID);
    }

    @Test
    void admitted_저장에_실패하면_waiting에서_꺼낸_사용자가_유실된다() {

        //given
        waitingQueueRepository.join(CONCERT_ID, 10L);
        waitingQueueRepository.join(CONCERT_ID, 20L);
        waitingQueueRepository.join(CONCERT_ID, 30L);

        willThrow(new RuntimeException("어드미션 저장 실패"))
            .given(admittedQueueRepository)
            .add(
                eq(CONCERT_ID),
                anyList()
            );

        //when & then
        assertThatThrownBy(
            () -> queueAdmissionService.admit(CONCERT_ID, 3L)
        )
            .isInstanceOf(RuntimeException.class);

        //ZPOPMIN은 이미 실행되었으므로 waiting에서는 사라짐.
        assertThat(waitingQueueRepository.getSize(CONCERT_ID))
            .isZero();

        //그런데 admitted는 저장에 실패했으므로 여기도 없음
        assertThat(redisTemplate.opsForZSet()
            .size("queue:admitted:" + CONCERT_ID)
        ).isZero();
    }
}