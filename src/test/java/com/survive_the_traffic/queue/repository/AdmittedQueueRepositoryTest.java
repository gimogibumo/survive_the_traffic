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
class AdmittedQueueRepositoryTest {

    private static final Long CONCERT_ID = 1L;

    @Autowired
    private AdmittedQueueRepository admittedQueueRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void tearDown() {
        redisTemplate.delete("queue:admitted:" + CONCERT_ID);
    }

    @Test
    void 입장이_허용된_사용자를_저장한다() {

        //given
        List<Long> userIds = List.of(10L, 20L, 30L);

        //when
        admittedQueueRepository.add(CONCERT_ID,userIds);

        //then
        assertThat(
            admittedQueueRepository.isAdmitted(
                CONCERT_ID,
                10L
            )
        ).isTrue();

        assertThat(
            admittedQueueRepository.isAdmitted(
                CONCERT_ID,
                20L
            )
        ).isTrue();

        assertThat(
            admittedQueueRepository.isAdmitted(
                CONCERT_ID,
                30L
            )
        ).isTrue();

        assertThat(
            admittedQueueRepository.getSize(CONCERT_ID)
        ).isEqualTo(3L);
    }

    @Test
    void 입장이_허용되지_않는_사용자_false를_반환한다() {

        //given
        admittedQueueRepository.add(CONCERT_ID,List.of(10L, 20L));

        //when
        boolean admitted = admittedQueueRepository.isAdmitted(CONCERT_ID, 30L);

        //then
        assertThat(admitted).isFalse();
    }

    @Test
    void 같은_사용자를_다시_추가해도_중복_저장되지_않는다() {

        //given
        admittedQueueRepository.add(CONCERT_ID,List.of(10L));

        //when
        admittedQueueRepository.add(CONCERT_ID,List.of(10L));

        //then
        assertThat(admittedQueueRepository.getSize(CONCERT_ID))
            .isEqualTo(1L);

        assertThat(admittedQueueRepository.isAdmitted(CONCERT_ID, 10L))
            .isTrue();
    }

    @Test
    void admission_score에는_만료시각이_저장된다() {

        //given
        long before = System.currentTimeMillis();

        //when
        admittedQueueRepository.add(CONCERT_ID,List.of(10L));

        long after = System.currentTimeMillis();

//        System.out.println(redisTemplate.opsForZSet().score("queue:admission:score", "queue:admitted:" + CONCERT_ID,0,-1));

        Double score =
            redisTemplate.opsForZSet().score("queue:admitted:" + CONCERT_ID, "10");

        //then
        assertThat(score).isNotNull();

        long expiresAt = score.longValue();

        long fiveMinutes = 5*60*1000L;

        assertThat(expiresAt).isBetween(before + fiveMinutes, after + fiveMinutes);
    }
}