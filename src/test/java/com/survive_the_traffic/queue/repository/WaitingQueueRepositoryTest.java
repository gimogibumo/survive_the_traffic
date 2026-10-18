package com.survive_the_traffic.queue.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.survive_the_traffic.queue.entity.QueueStatus;
import java.util.List;
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
         * pop 직전 테스트 fixture가 우리가 의도한 상태인지 확인한다.
         *
         * Worker가 테스트 중 개입한다면
         * 여기에서 바로 실패하므로 원인 파악도 쉬워진다.
         */
        assertThat(
            waitingQueueRepository
                .getSize(
                    CONCERT_ID
                )
        ).isEqualTo(
            5L
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
                .getRank(
                    CONCERT_ID,
                    20L
                )
        ).isEqualTo(
            1L
        );


        // when
        List<Long> popped =
            waitingQueueRepository
                .popFirst(
                    CONCERT_ID,
                    3L
                );


        // then
        assertThat(popped)
            .containsExactly(
                10L,
                20L,
                30L
            );


        assertThat(
            waitingQueueRepository
                .getSize(
                    CONCERT_ID
                )
        ).isEqualTo(
            2L
        );


        assertThat(
            waitingQueueRepository
                .getRank(
                    CONCERT_ID,
                    40L
                )
        ).isEqualTo(
            0L
        );


        assertThat(
            waitingQueueRepository
                .getRank(
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