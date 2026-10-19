package com.survive_the_traffic.queue.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
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

    @Disabled("\"Historical bug reproduction test. \"\n"
        + "        + \"Fixed by atomic Redis Lua script.\"")
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

    @Test
    void 새로운_admission을_처리할_때_만료된_admission을_정리한다() {

        // given
        Long concertId =
            1L;

        Long expiredUserId =
            999L;

        Long waitingUserId =
            10L;


        String admittedKey =
            "queue:admitted:"
                + concertId;


        String waitingKey =
            "queue:waiting:"
                + concertId;


        /*
         * 이미 만료된 admission을 직접 만든다.
         */
        redisTemplate.opsForZSet()
            .add(
                admittedKey,
                expiredUserId.toString(),
                System.currentTimeMillis()
                    - 10_000
            );


        /*
         * 새롭게 admission될 WAITING 사용자를 만든다.
         *
         * score는 여기서는 queue ordering만 중요하므로
         * 1을 사용한다.
         */
        redisTemplate.opsForZSet()
            .add(
                waitingKey,
                waitingUserId.toString(),
                1
            );


        assertThat(
            redisTemplate.opsForZSet()
                .score(
                    admittedKey,
                    expiredUserId.toString()
                )
        ).isNotNull();


        // when
        List<Long> admittedUsers =
            queueAdmissionRepository
                .admit(
                    concertId,
                    1L
                );


        // then
        assertThat(
            admittedUsers
        ).containsExactly(
            waitingUserId
        );


        /*
         * 기존 expired member는 cleanup되어야 한다.
         */
        Double expiredUserScore =
            redisTemplate.opsForZSet()
                .score(
                    admittedKey,
                    expiredUserId.toString()
                );


        assertThat(
            expiredUserScore
        ).isNull();


        /*
         * 새 admission은 정상적으로 존재해야 한다.
         */
        Double newUserScore =
            redisTemplate.opsForZSet()
                .score(
                    admittedKey,
                    waitingUserId.toString()
                );


        assertThat(
            newUserScore
        ).isNotNull();


        assertThat(
            newUserScore
        ).isGreaterThan(
            (double) System.currentTimeMillis()
        );


        /*
         * waiting에서는 제거되어야 한다.
         */
        Double waitingScore =
            redisTemplate.opsForZSet()
                .score(
                    waitingKey,
                    waitingUserId.toString()
                );


        assertThat(
            waitingScore
        ).isNull();
    }

    @Test
    void admission이_생성되면_admitted_key에도_만료시간이_설정된다() {

        // given
        Long concertId =
            1L;

        Long userId =
            10L;


        String admittedKey =
            "queue:admitted:"
                + concertId;


        String waitingKey =
            "queue:waiting:"
                + concertId;


        redisTemplate.opsForZSet()
            .add(
                waitingKey,
                userId.toString(),
                1
            );


        // when
        List<Long> admittedUsers =
            queueAdmissionRepository
                .admit(
                    concertId,
                    1L
                );


        // then
        assertThat(
            admittedUsers
        ).containsExactly(
            userId
        );


        Long ttlMillis =
            redisTemplate.getExpire(
                admittedKey,
                TimeUnit.MILLISECONDS
            );


        assertThat(
            ttlMillis
        ).isNotNull();


        /*
         * Redis:
         *
         * -1 = expiration 없음
         * -2 = key 없음
         *
         * 둘 다 나오면 안 된다.
         */
        assertThat(
            ttlMillis
        ).isPositive();


        /*
         * admission TTL은 5분이므로
         * 테스트 실행 시점에는 대략 300초 이하가 남아야 한다.
         */
        assertThat(
            ttlMillis
        ).isLessThanOrEqualTo(
            Duration.ofMinutes(5)
                .toMillis()
        );


        /*
         * 너무 짧은 TTL이 잘못 들어간 것도 잡는다.
         *
         * CI가 매우 느리더라도 이 테스트 직후 4분 이하로
         * 떨어질 이유는 사실상 없으므로 넉넉하게 4분을 사용한다.
         */
        assertThat(
            ttlMillis
        ).isGreaterThan(
            Duration.ofMinutes(4)
                .toMillis()
        );
    }

    @Test
    void waiting_사용자가_없어도_admit호출시_만료된_admission은_정리된다() {

        // given
        Long concertId =
            1L;

        Long expiredUserId =
            999L;


        String admittedKey =
            "queue:admitted:"
                + concertId;


        redisTemplate.opsForZSet()
            .add(
                admittedKey,
                expiredUserId.toString(),
                System.currentTimeMillis()
                    - 10_000
            );


        assertThat(
            redisTemplate.opsForZSet()
                .score(
                    admittedKey,
                    expiredUserId.toString()
                )
        ).isNotNull();


        // when
        List<Long> admittedUsers =
            queueAdmissionRepository
                .admit(
                    concertId,
                    10L
                );


        // then
        assertThat(
            admittedUsers
        ).isEmpty();


        Double expiredUserScore =
            redisTemplate.opsForZSet()
                .score(
                    admittedKey,
                    expiredUserId.toString()
                );


        assertThat(
            expiredUserScore
        ).isNull();


        /*
         * 유일한 member가 제거됐으므로
         * ZSET key 자체도 없어져야 한다.
         */
        Boolean exists =
            redisTemplate.hasKey(
                admittedKey
            );


        assertThat(
            exists
        ).isFalse();
    }
}