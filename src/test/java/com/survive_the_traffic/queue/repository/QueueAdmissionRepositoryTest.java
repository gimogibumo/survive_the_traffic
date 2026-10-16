package com.survive_the_traffic.queue.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
class QueueAdmissionRepositoryTest {

    private static final Long CONCERT_ID = 1L;

    @Autowired
    private QueueAdmissionRepository queueAdmissionRepository;

    @Autowired
    private WaitingQueueRepository waitingQueueRepository;

    @Autowired
    private AdmittedQueueRepository admittedQueueRepository;

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
    }


    @Test
    void waiting_앞에서부터_N명을_admitted로_전환한다() {
        // given
        waitingQueueRepository.join(CONCERT_ID, 10L);
        waitingQueueRepository.join(CONCERT_ID, 20L);
        waitingQueueRepository.join(CONCERT_ID, 30L);
        waitingQueueRepository.join(CONCERT_ID, 40L);
        waitingQueueRepository.join(CONCERT_ID, 50L);

        //when
        List<Long> admitted =
            queueAdmissionRepository.admit(CONCERT_ID, 3L);

        //then
        assertThat(waitingQueueRepository.getSize(CONCERT_ID))
            .isEqualTo(2L);
        assertThat(waitingQueueRepository.getRank(CONCERT_ID,50L))
            .isEqualTo(1L);
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
    }

    @Test
    void 여러_worker가_동시에_admission해도_같은_사용자가_중복_선택되지_않는다()
        throws Exception {

        // given
        for (long userId = 1; userId <= 100; userId++) {
            waitingQueueRepository.join(CONCERT_ID, userId);
        }

        ExecutorService executorService =
            Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<List<Long>> task = () -> {
            ready.countDown();

            // 두 thread가 준비될 때까지 대기
            start.await();

            return queueAdmissionRepository.admit(
                CONCERT_ID,
                30
            );
        };

        Future<List<Long>> first =
            executorService.submit(task);

        Future<List<Long>> second =
            executorService.submit(task);

        // 두 worker 모두 start 직전까지 도착했는지 확인
        ready.await();

        // 동시에 시작
        start.countDown();

        List<Long> firstResult = first.get();
        List<Long> secondResult = second.get();

        executorService.shutdown();

        // then
        assertThat(firstResult).hasSize(30);
        assertThat(secondResult).hasSize(30);

        Set<Long> firstSet =
            new HashSet<>(firstResult);

        Set<Long> secondSet =
            new HashSet<>(secondResult);

        assertThat(firstSet)
            .doesNotContainAnyElementsOf(secondSet);

        assertThat(
            waitingQueueRepository.getSize(CONCERT_ID)
        ).isEqualTo(40L);

        assertThat(
            admittedQueueRepository.getSize(CONCERT_ID)
        ).isEqualTo(60L);
    }
}