package com.survive_the_traffic.queue.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.survive_the_traffic.queue.dto.WaitingQueueJoinResult;
import com.survive_the_traffic.queue.entity.QueueStatus;
import com.survive_the_traffic.queue.entity.WaitingQueueJoinStatus;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WaitingQueueRepositoryTest {

    /*
     * 실제 개발 데이터나 다른 테스트에서 흔히 사용할 수 있는
     * concertId = 1L을 피하고 테스트 전용 ID를 사용한다.
     *
     * Waiting Queue Repository 자체는 Concert DB 존재 여부를
     * 확인하지 않으므로 임의의 테스트 ID를 사용해도 된다.
     */
    private static final Long CONCERT_ID =
        9_990_001L;


    @Autowired
    private WaitingQueueRepository
        waitingQueueRepository;

    @Autowired
    private StringRedisTemplate
        redisTemplate;

    @Autowired
    private QueueStatusRepository
        queueStatusRepository;

    @Autowired
    private QueueAdmissionRepository
        queueAdmissionRepository;


    /*
     * @AfterEach만 사용하면 테스트 실행 전에
     * 이전 애플리케이션 실행이나 다른 테스트에서 남긴
     * Redis 데이터가 존재할 수 있다.
     *
     * 따라서 각 테스트가 시작되기 전에도
     * 반드시 clean state를 만든다.
     */
    @BeforeEach
    void setUp() {

        clearRedisState();
    }


    /*
     * 다음 테스트나 이후 로컬 실행에
     * 테스트 데이터가 남지 않도록 종료 후에도 정리한다.
     */
    @AfterEach
    void tearDown() {

        clearRedisState();
    }


    @Test
    void 대기열에_들어온_순서대로_rank가_부여된다() {

        // given & when
        boolean first =
            waitingQueueRepository
                .join(
                    CONCERT_ID,
                    10L
                )
                .added();

        boolean second =
            waitingQueueRepository
                .join(
                    CONCERT_ID,
                    20L
                )
                .added();

        boolean third =
            waitingQueueRepository
                .join(
                    CONCERT_ID,
                    30L
                )
                .added();


        // then
        assertThat(first)
            .isTrue();

        assertThat(second)
            .isTrue();

        assertThat(third)
            .isTrue();


        assertThat(
            waitingQueueRepository
                .getRank(
                    CONCERT_ID,
                    10L
                )
        ).isEqualTo(
            0L
        );


        assertThat(
            waitingQueueRepository
                .getRank(
                    CONCERT_ID,
                    20L
                )
        ).isEqualTo(
            1L
        );


        assertThat(
            waitingQueueRepository
                .getRank(
                    CONCERT_ID,
                    30L
                )
        ).isEqualTo(
            2L
        );


        assertThat(
            waitingQueueRepository
                .getSize(
                    CONCERT_ID
                )
        ).isEqualTo(
            3L
        );
    }


    @Test
    void 같은_사용자가_다시_join해도_대기열_순서는_변하지_않는다() {

        // given
        waitingQueueRepository.join(
            CONCERT_ID,
            10L
        );

        waitingQueueRepository.join(
            CONCERT_ID,
            20L
        );

        waitingQueueRepository.join(
            CONCERT_ID,
            30L
        );


        // when
        boolean added =
            waitingQueueRepository
                .join(
                    CONCERT_ID,
                    20L
                )
                .added();


        // then
        assertThat(added)
            .isFalse();


        assertThat(
            waitingQueueRepository
                .getRank(
                    CONCERT_ID,
                    20L
                )
        ).isEqualTo(
            1L
        );


        assertThat(
            waitingQueueRepository
                .getSize(
                    CONCERT_ID
                )
        ).isEqualTo(
            3L
        );
    }


    @Test
    void 앞에서부터_N명의_사용자를_꺼낸다() {

        // given
        waitingQueueRepository.join(
            CONCERT_ID,
            10L
        );

        waitingQueueRepository.join(
            CONCERT_ID,
            20L
        );

        waitingQueueRepository.join(
            CONCERT_ID,
            30L
        );

        waitingQueueRepository.join(
            CONCERT_ID,
            40L
        );

        waitingQueueRepository.join(
            CONCERT_ID,
            50L
        );


        /*
         * popFirst()를 호출하기 직전에
         * Redis ZSET 자체를 직접 검사한다.
         *
         * 여기서 이미 값이 사라져 있다면
         * popFirst 문제가 아니라 다른 실행 주체가
         * queue를 변경한 것이다.
         */
        Set<String> waitingBeforePop =
            redisTemplate
                .opsForZSet()
                .range(
                    waitingKey(),
                    0,
                    -1
                );


        assertThat(waitingBeforePop)
            .isNotNull();


        assertThat(waitingBeforePop)
            .containsExactly(
                "10",
                "20",
                "30",
                "40",
                "50"
            );


        assertThat(
            waitingQueueRepository.getSize(
                CONCERT_ID
            )
        ).isEqualTo(
            5L
        );


        assertThat(
            waitingQueueRepository.getRank(
                CONCERT_ID,
                10L
            )
        ).isEqualTo(
            0L
        );


        assertThat(
            waitingQueueRepository.getRank(
                CONCERT_ID,
                20L
            )
        ).isEqualTo(
            1L
        );


        assertThat(
            waitingQueueRepository.getRank(
                CONCERT_ID,
                30L
            )
        ).isEqualTo(
            2L
        );


        assertThat(
            waitingQueueRepository.getRank(
                CONCERT_ID,
                40L
            )
        ).isEqualTo(
            3L
        );


        assertThat(
            waitingQueueRepository.getRank(
                CONCERT_ID,
                50L
            )
        ).isEqualTo(
            4L
        );


        // when
        List<Long> popped =
            waitingQueueRepository.popFirst(
                CONCERT_ID,
                3L
            );


        // then
        assertThat(popped)
            .isNotNull();


        assertThat(popped)
            .containsExactly(
                10L,
                20L,
                30L
            );


        Set<String> waitingAfterPop =
            redisTemplate
                .opsForZSet()
                .range(
                    waitingKey(),
                    0,
                    -1
                );


        assertThat(waitingAfterPop)
            .isNotNull();


        assertThat(waitingAfterPop)
            .containsExactly(
                "40",
                "50"
            );


        assertThat(
            waitingQueueRepository.getSize(
                CONCERT_ID
            )
        ).isEqualTo(
            2L
        );


        assertThat(
            waitingQueueRepository.getRank(
                CONCERT_ID,
                40L
            )
        ).isEqualTo(
            0L
        );


        assertThat(
            waitingQueueRepository.getRank(
                CONCERT_ID,
                50L
            )
        ).isEqualTo(
            1L
        );
    }


    @Test
    void 중복_join은_sequence를_추가로_소비하지_않는다() {

        // given
        boolean first =
            waitingQueueRepository
                .join(
                    CONCERT_ID,
                    10L
                )
                .added();


        // when
        boolean second =
            waitingQueueRepository
                .join(
                    CONCERT_ID,
                    10L
                )
                .added();


        // then
        assertThat(first)
            .isTrue();

        assertThat(second)
            .isFalse();


        String sequence =
            redisTemplate
                .opsForValue()
                .get(
                    sequenceKey()
                );


        assertThat(sequence)
            .isEqualTo(
                "1"
            );


        assertThat(
            waitingQueueRepository
                .getRank(
                    CONCERT_ID,
                    10L
                )
        ).isEqualTo(
            0L
        );


        assertThat(
            waitingQueueRepository
                .getSize(
                    CONCERT_ID
                )
        ).isEqualTo(
            1L
        );
    }


    @Test
    void 중복_join은_누락된_active_concert를_복구한다() {

        // given
        waitingQueueRepository.join(
            CONCERT_ID,
            10L
        );


        redisTemplate
            .opsForSet()
            .remove(
                activeConcertsKey(),
                CONCERT_ID.toString()
            );


        Boolean before =
            redisTemplate
                .opsForSet()
                .isMember(
                    activeConcertsKey(),
                    CONCERT_ID.toString()
                );


        assertThat(before)
            .isFalse();


        // when
        boolean added =
            waitingQueueRepository
                .join(
                    CONCERT_ID,
                    10L
                )
                .added();


        // then
        assertThat(added)
            .isFalse();


        Boolean after =
            redisTemplate
                .opsForSet()
                .isMember(
                    activeConcertsKey(),
                    CONCERT_ID.toString()
                );


        assertThat(after)
            .isTrue();


        assertThat(
            redisTemplate
                .opsForValue()
                .get(
                    sequenceKey()
                )
        ).isEqualTo(
            "1"
        );
    }


    @Test
    void waiting_queue에_처음_join하면_rank와_size를_반환한다() {

        // when
        WaitingQueueJoinResult result =
            waitingQueueRepository
                .join(
                    CONCERT_ID,
                    10L
                );


        // then
        assertThat(
            result.added()
        ).isTrue();


        assertThat(
            result.rank()
        ).isEqualTo(
            0L
        );


        assertThat(
            result.waitingCount()
        ).isEqualTo(
            1L
        );
    }


    @Test
    void join_순서대로_rank를_반환한다() {

        // when
        WaitingQueueJoinResult first =
            waitingQueueRepository
                .join(
                    CONCERT_ID,
                    10L
                );


        WaitingQueueJoinResult second =
            waitingQueueRepository
                .join(
                    CONCERT_ID,
                    20L
                );


        // then
        assertThat(
            first.rank()
        ).isEqualTo(
            0L
        );


        assertThat(
            first.waitingCount()
        ).isEqualTo(
            1L
        );


        assertThat(
            second.rank()
        ).isEqualTo(
            1L
        );


        assertThat(
            second.waitingCount()
        ).isEqualTo(
            2L
        );
    }


    @Test
    void 중복_join은_기존_rank를_유지하고_sequence를_소비하지_않는다() {

        // given
        WaitingQueueJoinResult first =
            waitingQueueRepository
                .join(
                    CONCERT_ID,
                    10L
                );


        // when
        WaitingQueueJoinResult second =
            waitingQueueRepository
                .join(
                    CONCERT_ID,
                    10L
                );


        // then
        assertThat(
            first.added()
        ).isTrue();


        assertThat(
            second.added()
        ).isFalse();


        assertThat(
            second.rank()
        ).isEqualTo(
            0L
        );


        assertThat(
            second.waitingCount()
        ).isEqualTo(
            1L
        );

        assertThat(
            first.status()
        ).isEqualTo(
            WaitingQueueJoinStatus.JOINED
        );

        assertThat(
            second.status()
        ).isEqualTo(
            WaitingQueueJoinStatus.ALREADY_WAITING
        );


        String sequence =
            redisTemplate
                .opsForValue()
                .get(
                    sequenceKey()
                );


        assertThat(sequence)
            .isEqualTo(
                "1"
            );
    }


    /*
     * 이 테스트는 historical bug reproduction이 아니다.
     *
     * 현재 queue-status.lua가 WAITING 상태와 rank,
     * waitingCount를 올바르게 반환하는지를 검증하는
     * 정상 regression test다.
     */
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
            queueStatusRepository
                .getStatus(
                    CONCERT_ID,
                    20L
                );


        // then
        assertThat(
            result.status()
        ).isEqualTo(
            QueueStatus.WAITING
        );


        assertThat(
            result.rank()
        ).isEqualTo(
            1L
        );


        assertThat(
            result.waitingCount()
        ).isEqualTo(
            2L
        );
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
            1
        );


        // when
        QueueStatusResult result =
            queueStatusRepository
                .getStatus(
                    CONCERT_ID,
                    10L
                );


        // then
        assertThat(
            result.status()
        ).isEqualTo(
            QueueStatus.ADMITTED
        );


        assertThat(
            result.rank()
        ).isNull();


        assertThat(
            result.waitingCount()
        ).isEqualTo(
            1L
        );
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
            queueStatusRepository
                .getStatus(
                    CONCERT_ID,
                    999L
                );


        // then
        assertThat(
            result.status()
        ).isEqualTo(
            QueueStatus.NOT_JOINED
        );


        assertThat(
            result.rank()
        ).isNull();


        assertThat(
            result.waitingCount()
        ).isEqualTo(
            1L
        );
    }


    @Test
    void 만료된_admission은_ADMITTED로_판단하지_않는다() {

        // given
        redisTemplate
            .opsForZSet()
            .add(
                admittedKey(),
                "10",
                System.currentTimeMillis()
                    - 1_000
            );


        // when
        QueueStatusResult result =
            queueStatusRepository
                .getStatus(
                    CONCERT_ID,
                    10L
                );


        // then
        assertThat(
            result.status()
        ).isEqualTo(
            QueueStatus.NOT_JOINED
        );


        assertThat(
            result.rank()
        ).isNull();
    }

    @Test
    void admitted_사용자가_다시_join해도_waiting에_들어가지_않는다() {

        // given
        Long userId =
            10L;


        WaitingQueueJoinResult firstJoin =
            waitingQueueRepository.join(
                CONCERT_ID,
                userId
            );


        assertThat(
            firstJoin.status()
        ).isEqualTo(
            WaitingQueueJoinStatus.JOINED
        );


        assertThat(
            firstJoin.added()
        ).isTrue();


        List<Long> admittedUsers =
            queueAdmissionRepository.admit(
                CONCERT_ID,
                1L
            );


        assertThat(
            admittedUsers
        ).containsExactly(
            userId
        );


        /*
         * admission 이후 waiting에서는 제거된다.
         */
        assertThat(
            waitingQueueRepository.getRank(
                CONCERT_ID,
                userId
            )
        ).isNull();


        assertThat(
            waitingQueueRepository.getSize(
                CONCERT_ID
            )
        ).isZero();


        Double admittedScoreBeforeRejoin =
            redisTemplate
                .opsForZSet()
                .score(
                    admittedKey(),
                    userId.toString()
                );


        assertThat(
            admittedScoreBeforeRejoin
        ).isNotNull();


        /*
         * when
         *
         * 이미 admission을 받은 사용자가 다시 join한다.
         */
        WaitingQueueJoinResult secondJoin =
            waitingQueueRepository.join(
                CONCERT_ID,
                userId
            );


        /*
         * then
         *
         * 신규 join으로 판단하면 안 된다.
         */
        assertThat(
            secondJoin.status()
        ).isEqualTo(
            WaitingQueueJoinStatus.ALREADY_ADMITTED
        );


        assertThat(
            secondJoin.added()
        ).isFalse();


        assertThat(
            secondJoin.rank()
        ).isEqualTo(
            -1L
        );


        assertThat(
            secondJoin.waitingCount()
        ).isZero();


        /*
         * waiting queue에는 재등록되지 않는다.
         */
        assertThat(
            waitingQueueRepository.getRank(
                CONCERT_ID,
                userId
            )
        ).isNull();


        assertThat(
            waitingQueueRepository.getSize(
                CONCERT_ID
            )
        ).isZero();


        /*
         * 기존 admission은 그대로 유지된다.
         */
        Double admittedScoreAfterRejoin =
            redisTemplate
                .opsForZSet()
                .score(
                    admittedKey(),
                    userId.toString()
                );


        assertThat(
            admittedScoreAfterRejoin
        ).isNotNull();


        assertThat(
            admittedScoreAfterRejoin
        ).isEqualTo(
            admittedScoreBeforeRejoin
        );
    }


    @Test
    void 만료된_admission_사용자는_다시_waiting에_join할_수_있다() {

        // given
        Long userId =
            10L;


        redisTemplate
            .opsForZSet()
            .add(
                admittedKey(),
                userId.toString(),
                System.currentTimeMillis()
                    - 1_000
            );


        Double expiredAdmission =
            redisTemplate
                .opsForZSet()
                .score(
                    admittedKey(),
                    userId.toString()
                );


        assertThat(
            expiredAdmission
        ).isNotNull();


        // when
        WaitingQueueJoinResult result =
            waitingQueueRepository.join(
                CONCERT_ID,
                userId
            );


        // then
        assertThat(
            result.status()
        ).isEqualTo(
            WaitingQueueJoinStatus.JOINED
        );


        assertThat(
            result.added()
        ).isTrue();


        assertThat(
            result.rank()
        ).isEqualTo(
            0L
        );


        assertThat(
            result.waitingCount()
        ).isEqualTo(
            1L
        );


        /*
         * stale admitted entry는 제거되어야 한다.
         */
        Double admittedAfterJoin =
            redisTemplate
                .opsForZSet()
                .score(
                    admittedKey(),
                    userId.toString()
                );


        assertThat(
            admittedAfterJoin
        ).isNull();


        /*
         * 대신 waiting에는 존재해야 한다.
         */
        assertThat(
            waitingQueueRepository.getRank(
                CONCERT_ID,
                userId
            )
        ).isEqualTo(
            0L
        );
    }

    @Test
    void admit과_rejoin이_동시에_실행되어도_최종상태는_ADMITTED_only이다()
        throws Exception {

        Long userId = 10L;

        int repeatCount = 100;


        for (int i = 0; i < repeatCount; i++) {

            /*
             * 각 race 실험을 독립적으로 만든다.
             */
            redisTemplate.delete(
                "queue:waiting:" + CONCERT_ID
            );

            redisTemplate.delete(
                "queue:sequence:" + CONCERT_ID
            );

            redisTemplate.delete(
                "queue:admitted:" + CONCERT_ID
            );

            redisTemplate.opsForSet()
                .remove(
                    "queue:active-concerts",
                    CONCERT_ID.toString()
                );


            /*
             * given
             *
             * user는 먼저 WAITING 상태에 둔다.
             */
            WaitingQueueJoinResult initialJoin =
                waitingQueueRepository.join(
                    CONCERT_ID,
                    userId
                );


            assertThat(
                initialJoin.status()
            ).isEqualTo(
                WaitingQueueJoinStatus.JOINED
            );


            assertThat(
                waitingQueueRepository.getRank(
                    CONCERT_ID,
                    userId
                )
            ).isEqualTo(
                0L
            );


            ExecutorService executorService =
                Executors.newFixedThreadPool(2);


            CountDownLatch readyLatch =
                new CountDownLatch(2);

            CountDownLatch startLatch =
                new CountDownLatch(1);


            try {

                /*
                 * Thread A
                 *
                 * Worker 역할:
                 * WAITING -> ADMITTED
                 */
                Future<List<Long>> admitFuture =
                    executorService.submit(
                        () -> {

                            readyLatch.countDown();

                            startLatch.await();

                            return queueAdmissionRepository
                                .admit(
                                    CONCERT_ID,
                                    1L
                                );
                        }
                    );


                /*
                 * Thread B
                 *
                 * 사용자가 동시에 다시 join을 호출한다.
                 */
                Future<WaitingQueueJoinResult> rejoinFuture =
                    executorService.submit(
                        () -> {

                            readyLatch.countDown();

                            startLatch.await();

                            return waitingQueueRepository
                                .join(
                                    CONCERT_ID,
                                    userId
                                );
                        }
                    );


                /*
                 * 두 thread가 모두 준비될 때까지 기다린 뒤
                 * 최대한 같은 시점에 시작시킨다.
                 */
                boolean ready =
                    readyLatch.await(
                        3,
                        TimeUnit.SECONDS
                    );


                assertThat(ready)
                    .isTrue();


                startLatch.countDown();


                List<Long> admittedUsers =
                    admitFuture.get(
                        3,
                        TimeUnit.SECONDS
                    );


                WaitingQueueJoinResult rejoinResult =
                    rejoinFuture.get(
                        3,
                        TimeUnit.SECONDS
                    );


                /*
                 * admit은 반드시 이 사용자를 ADMITTED로 이동시켜야 한다.
                 */
                assertThat(
                    admittedUsers
                ).containsExactly(
                    userId
                );


                /*
                 * race 순서에 따라 rejoin 결과는 둘 중 하나다.
                 *
                 * 1. rejoin이 먼저 실행:
                 *    ALREADY_WAITING
                 *
                 * 2. admit이 먼저 실행:
                 *    ALREADY_ADMITTED
                 */
                assertThat(
                    rejoinResult.status()
                ).isIn(
                    WaitingQueueJoinStatus.ALREADY_WAITING,
                    WaitingQueueJoinStatus.ALREADY_ADMITTED
                );


                /*
                 * 핵심 invariant 1:
                 *
                 * 최종적으로 waiting에는 없어야 한다.
                 */
                assertThat(
                    waitingQueueRepository.getRank(
                        CONCERT_ID,
                        userId
                    )
                ).isNull();


                assertThat(
                    waitingQueueRepository.getSize(
                        CONCERT_ID
                    )
                ).isZero();


                /*
                 * 핵심 invariant 2:
                 *
                 * admitted에는 반드시 존재해야 한다.
                 */
                Double admittedScore =
                    redisTemplate
                        .opsForZSet()
                        .score(
                            "queue:admitted:"
                                + CONCERT_ID,
                            userId.toString()
                        );


                assertThat(
                    admittedScore
                ).isNotNull();


                assertThat(
                    admittedScore
                ).isGreaterThan(
                    (double) System.currentTimeMillis()
                );


                /*
                 * 핵심 invariant 3:
                 *
                 * 외부에서 조회하는 logical status 역시
                 * ADMITTED여야 한다.
                 */
                QueueStatusResult statusResult =
                    queueStatusRepository
                        .getStatus(
                            CONCERT_ID,
                            userId
                        );


                assertThat(
                    statusResult.status()
                ).isEqualTo(
                    QueueStatus.ADMITTED
                );


                assertThat(
                    statusResult.rank()
                ).isNull();


                assertThat(
                    statusResult.waitingCount()
                ).isZero();

            } finally {

                executorService.shutdownNow();

                executorService.awaitTermination(
                    3,
                    TimeUnit.SECONDS
                );
            }
        }
    }

    @Test
    void 만료된_admission에_동시에_rejoin해도_waiting에는_한번만_등록된다()
        throws Exception {

        Long userId = 10L;

        int repeatCount = 100;


        for (int i = 0; i < repeatCount; i++) {

            /*
             * 각 race 실험을 독립적인 Redis 상태에서 시작한다.
             */
            redisTemplate.delete(
                "queue:waiting:" + CONCERT_ID
            );

            redisTemplate.delete(
                "queue:sequence:" + CONCERT_ID
            );

            redisTemplate.delete(
                "queue:admitted:" + CONCERT_ID
            );

            redisTemplate.opsForSet()
                .remove(
                    "queue:active-concerts",
                    CONCERT_ID.toString()
                );


            /*
             * given
             *
             * 사용자는 admitted ZSET에는 존재하지만
             * expiresAt은 이미 지난 상태다.
             */
            redisTemplate.opsForZSet()
                .add(
                    "queue:admitted:" + CONCERT_ID,
                    userId.toString(),
                    System.currentTimeMillis() - 10_000
                );


            Double expiredAdmissionScore =
                redisTemplate.opsForZSet()
                    .score(
                        "queue:admitted:" + CONCERT_ID,
                        userId.toString()
                    );


            assertThat(
                expiredAdmissionScore
            ).isNotNull();


            ExecutorService executorService =
                Executors.newFixedThreadPool(2);


            CountDownLatch readyLatch =
                new CountDownLatch(2);

            CountDownLatch startLatch =
                new CountDownLatch(1);


            try {

                Future<WaitingQueueJoinResult> firstFuture =
                    executorService.submit(
                        () -> {

                            readyLatch.countDown();

                            startLatch.await();

                            return waitingQueueRepository
                                .join(
                                    CONCERT_ID,
                                    userId
                                );
                        }
                    );


                Future<WaitingQueueJoinResult> secondFuture =
                    executorService.submit(
                        () -> {

                            readyLatch.countDown();

                            startLatch.await();

                            return waitingQueueRepository
                                .join(
                                    CONCERT_ID,
                                    userId
                                );
                        }
                    );


                boolean ready =
                    readyLatch.await(
                        3,
                        TimeUnit.SECONDS
                    );


                assertThat(
                    ready
                ).isTrue();


                startLatch.countDown();


                WaitingQueueJoinResult firstResult =
                    firstFuture.get(
                        3,
                        TimeUnit.SECONDS
                    );


                WaitingQueueJoinResult secondResult =
                    secondFuture.get(
                        3,
                        TimeUnit.SECONDS
                    );


                /*
                 * 두 요청 중 정확히 하나만 신규 JOIN이어야 한다.
                 *
                 * Redis Lua script는 atomic하게 실행되므로:
                 *
                 * 첫 번째 script:
                 * expired ADMITTED 제거
                 * -> waiting 신규 등록
                 * -> JOINED
                 *
                 * 두 번째 script:
                 * waiting 존재 확인
                 * -> ALREADY_WAITING
                 */
                List<WaitingQueueJoinStatus> statuses =
                    List.of(
                        firstResult.status(),
                        secondResult.status()
                    );


                assertThat(
                    statuses
                ).containsExactlyInAnyOrder(
                    WaitingQueueJoinStatus.JOINED,
                    WaitingQueueJoinStatus.ALREADY_WAITING
                );


                /*
                 * 최종 waiting entry는 하나뿐이다.
                 */
                assertThat(
                    waitingQueueRepository.getSize(
                        CONCERT_ID
                    )
                ).isEqualTo(
                    1L
                );


                assertThat(
                    waitingQueueRepository.getRank(
                        CONCERT_ID,
                        userId
                    )
                ).isEqualTo(
                    0L
                );


                /*
                 * sequence 역시 딱 한 번만 소비되어야 한다.
                 *
                 * 중복 rejoin이 sequence까지 증가시키면
                 * queue ordering에 불필요한 gap이 생긴다.
                 */
                String sequence =
                    redisTemplate.opsForValue()
                        .get(
                            "queue:sequence:"
                                + CONCERT_ID
                        );


                assertThat(
                    sequence
                ).isEqualTo(
                    "1"
                );


                /*
                 * 만료된 admission entry는 제거되어야 한다.
                 */
                Double admittedScoreAfterRace =
                    redisTemplate.opsForZSet()
                        .score(
                            "queue:admitted:"
                                + CONCERT_ID,
                            userId.toString()
                        );


                assertThat(
                    admittedScoreAfterRace
                ).isNull();


                /*
                 * 외부 logical status도 WAITING이어야 한다.
                 */
                QueueStatusResult statusResult =
                    queueStatusRepository
                        .getStatus(
                            CONCERT_ID,
                            userId
                        );


                assertThat(
                    statusResult.status()
                ).isEqualTo(
                    QueueStatus.WAITING
                );


                assertThat(
                    statusResult.rank()
                ).isEqualTo(
                    0L
                );


                assertThat(
                    statusResult.waitingCount()
                ).isEqualTo(
                    1L
                );

            } finally {

                executorService.shutdownNow();

                executorService.awaitTermination(
                    3,
                    TimeUnit.SECONDS
                );
            }
        }
    }




    /*
     * 테스트가 다루는 Redis state를
     * 시작 전 / 종료 후 동일하게 제거한다.
     *
     * 특히 admitted/reserving을 빼먹으면
     * queue-status.lua가 ADMITTED를 우선 판단하기 때문에
     * 다른 테스트가 오염될 수 있다.
     */
    private void clearRedisState() {

        redisTemplate.delete(
            waitingKey()
        );

        redisTemplate.delete(
            sequenceKey()
        );

        redisTemplate.delete(
            admittedKey()
        );


        /*
         * queue:active-concerts 전체 SET을 삭제하지 않고
         * 이 테스트에서 사용하는 concertId만 제거한다.
         *
         * 전체 DEL을 하면 다른 테스트나 병렬 테스트의
         * active concert 상태까지 제거할 수 있다.
         */
        redisTemplate
            .opsForSet()
            .remove(
                activeConcertsKey(),
                CONCERT_ID.toString()
            );
    }


    private String waitingKey() {

        return "queue:waiting:"
            + CONCERT_ID;
    }


    private String sequenceKey() {

        return "queue:sequence:"
            + CONCERT_ID;
    }


    private String admittedKey() {

        return "queue:admitted:"
            + CONCERT_ID;
    }




    private String activeConcertsKey() {

        return "queue:active-concerts";
    }
}