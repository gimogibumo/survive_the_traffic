package com.survive_the_traffic.queue.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.survive_the_traffic.queue.dto.WaitingQueueJoinResult;
import com.survive_the_traffic.queue.entity.QueueStatus;
import com.survive_the_traffic.queue.entity.WaitingQueueJoinStatus;
import java.util.List;
import java.util.Set;
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

        redisTemplate.delete(
            reservingKey()
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


    private String reservingKey() {

        return "queue:reserving:"
            + CONCERT_ID;
    }


    private String activeConcertsKey() {

        return "queue:active-concerts";
    }
}