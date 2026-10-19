package com.survive_the_traffic.queue.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ActiveConcertRepositoryTest {

    private static final String ACTIVE_CONCERTS_KEY =
        "queue:active-concerts";

    @Autowired
    private ActiveConcertRepository activeConcertRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void tearDown() {
        redisTemplate.delete(ACTIVE_CONCERTS_KEY);
    }

    @Test
    void concert를_active로_등록한다() {

        //when
        boolean activated =
            activeConcertRepository.activate(1L);

        //then
        assertThat(activated).isTrue();

        Set<Long> activeConcerts =
            activeConcertRepository.findAll();

        assertThat(activeConcerts).containsExactly(1L);
    }

    @Test
    void 여러_active_concert를_조회한다() {

        // given
        activeConcertRepository.activate(1L);
        activeConcertRepository.activate(3L);
        activeConcertRepository.activate(7L);

        // when
        Set<Long> activeConcerts =
            activeConcertRepository.findAll();

        //then
        assertThat(activeConcerts).containsExactlyInAnyOrder(1L, 3L, 7L);
    }

    @Test
    void 같은_concert를_여러번_activate해도_중복되지_않는다() {

        // given
        boolean first =
            activeConcertRepository.activate(1L);

        // when
        boolean second =
            activeConcertRepository.activate(1L);

        // then
        assertThat(first).isTrue();
        assertThat(second).isFalse();

        assertThat(
            activeConcertRepository.findAll()
        ).containsExactly(1L);
    }

    @Test
    void active_concert가_없으면_빈_set을_반환한다() {

        // when
        Set<Long> activeConcerts =
            activeConcertRepository.findAll();

        // then
        assertThat(activeConcerts).isEmpty();
    }

}