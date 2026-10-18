package com.survive_the_traffic.queue.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.survive_the_traffic.queue.dto.AdmissionClaimResult;
import com.survive_the_traffic.queue.entity.AdmissionClaimStatus;
import com.survive_the_traffic.queue.entity.AdmissionRestoreStatus;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
class AdmissionClaimRepositoryTest {

    private static final Long CONCERT_ID =
        9_000_001L;

    private static final Long USER_ID =
        10L;


    @Autowired
    private AdmissionClaimRepository
        admissionClaimRepository;

    @Autowired
    private StringRedisTemplate
        redisTemplate;


    @AfterEach
    void tearDown() {

        redisTemplate.delete(
            admittedKey()
        );

        redisTemplate.delete(
            reservingKey()
        );
    }


    @Test
    void admitted_사용자는_claim할_수_있다() {

        // given
        long expiresAt =
            System.currentTimeMillis()
                + 300_000;


        redisTemplate
            .opsForZSet()
            .add(
                admittedKey(),
                USER_ID.toString(),
                expiresAt
            );


        // when
        AdmissionClaimResult result =
            admissionClaimRepository
                .claim(
                    CONCERT_ID,
                    USER_ID
                );


        // then
        assertThat(result.status())
            .isEqualTo(
                AdmissionClaimStatus
                    .CLAIMED
            );

        assertThat(result.expiresAt())
            .isEqualTo(expiresAt);


        Double admittedScore =
            redisTemplate
                .opsForZSet()
                .score(
                    admittedKey(),
                    USER_ID.toString()
                );


        Double reservingScore =
            redisTemplate
                .opsForZSet()
                .score(
                    reservingKey(),
                    USER_ID.toString()
                );


        assertThat(admittedScore)
            .isNull();

        assertThat(reservingScore)
            .isEqualTo(
                (double) expiresAt
            );
    }


    @Test
    void admitted되지_않은_사용자는_claim할_수_없다() {

        // when
        AdmissionClaimResult result =
            admissionClaimRepository
                .claim(
                    CONCERT_ID,
                    USER_ID
                );


        // then
        assertThat(result.status())
            .isEqualTo(
                AdmissionClaimStatus
                    .NOT_ADMITTED
            );

        assertThat(result.expiresAt())
            .isNull();
    }


    @Test
    void 만료된_admission은_claim할_수_없다() {

        // given
        long expiredAt =
            System.currentTimeMillis()
                - 1_000;


        redisTemplate
            .opsForZSet()
            .add(
                admittedKey(),
                USER_ID.toString(),
                expiredAt
            );


        // when
        AdmissionClaimResult result =
            admissionClaimRepository
                .claim(
                    CONCERT_ID,
                    USER_ID
                );


        // then
        assertThat(result.status())
            .isEqualTo(
                AdmissionClaimStatus
                    .NOT_ADMITTED
            );


        assertThat(
            redisTemplate
                .opsForZSet()
                .score(
                    admittedKey(),
                    USER_ID.toString()
                )
        ).isNull();


        assertThat(
            redisTemplate
                .opsForZSet()
                .score(
                    reservingKey(),
                    USER_ID.toString()
                )
        ).isNull();
    }


    @Test
    void 같은_admission은_두_번_claim할_수_없다() {

        // given
        long expiresAt =
            System.currentTimeMillis()
                + 300_000;


        redisTemplate
            .opsForZSet()
            .add(
                admittedKey(),
                USER_ID.toString(),
                expiresAt
            );


        // when
        AdmissionClaimResult first =
            admissionClaimRepository
                .claim(
                    CONCERT_ID,
                    USER_ID
                );


        AdmissionClaimResult second =
            admissionClaimRepository
                .claim(
                    CONCERT_ID,
                    USER_ID
                );


        // then
        assertThat(first.status())
            .isEqualTo(
                AdmissionClaimStatus
                    .CLAIMED
            );


        assertThat(second.status())
            .isEqualTo(
                AdmissionClaimStatus
                    .ALREADY_RESERVING
            );
    }


    @Test
    void 동일한_admission을_동시에_claim하면_한_요청만_성공한다()
        throws Exception {

        // given
        long expiresAt =
            System.currentTimeMillis()
                + 300_000;


        redisTemplate
            .opsForZSet()
            .add(
                admittedKey(),
                USER_ID.toString(),
                expiresAt
            );


        ExecutorService executor =
            Executors.newFixedThreadPool(2);


        CountDownLatch start =
            new CountDownLatch(1);


        try {

            Future<AdmissionClaimResult> firstFuture =
                executor.submit(
                    () -> {

                        start.await();

                        return admissionClaimRepository
                            .claim(
                                CONCERT_ID,
                                USER_ID
                            );
                    }
                );


            Future<AdmissionClaimResult> secondFuture =
                executor.submit(
                    () -> {

                        start.await();

                        return admissionClaimRepository
                            .claim(
                                CONCERT_ID,
                                USER_ID
                            );
                    }
                );


            // when
            start.countDown();


            AdmissionClaimResult first =
                firstFuture.get(
                    2,
                    TimeUnit.SECONDS
                );


            AdmissionClaimResult second =
                secondFuture.get(
                    2,
                    TimeUnit.SECONDS
                );


            // then
            List<AdmissionClaimStatus> statuses =
                List.of(
                    first.status(),
                    second.status()
                );


            assertThat(statuses)
                .containsExactlyInAnyOrder(
                    AdmissionClaimStatus
                        .CLAIMED,

                    AdmissionClaimStatus
                        .ALREADY_RESERVING
                );


            assertThat(
                redisTemplate
                    .opsForZSet()
                    .score(
                        admittedKey(),
                        USER_ID.toString()
                    )
            ).isNull();


            assertThat(
                redisTemplate
                    .opsForZSet()
                    .score(
                        reservingKey(),
                        USER_ID.toString()
                    )
            ).isNotNull();

        } finally {

            executor.shutdownNow();
        }
    }


    @Test
    void reservation이_성공하면_claim을_소비한다() {

        // given
        long expiresAt =
            System.currentTimeMillis()
                + 300_000;


        redisTemplate
            .opsForZSet()
            .add(
                admittedKey(),
                USER_ID.toString(),
                expiresAt
            );


        AdmissionClaimResult claimResult =
            admissionClaimRepository
                .claim(
                    CONCERT_ID,
                    USER_ID
                );


        assertThat(claimResult.status())
            .isEqualTo(
                AdmissionClaimStatus
                    .CLAIMED
            );


        // when
        boolean completed =
            admissionClaimRepository
                .complete(
                    CONCERT_ID,
                    USER_ID
                );


        // then
        assertThat(completed)
            .isTrue();


        assertThat(
            redisTemplate
                .opsForZSet()
                .score(
                    admittedKey(),
                    USER_ID.toString()
                )
        ).isNull();


        assertThat(
            redisTemplate
                .opsForZSet()
                .score(
                    reservingKey(),
                    USER_ID.toString()
                )
        ).isNull();
    }


    @Test
    void reservation이_실패하면_admission을_복구한다() {

        // given
        long expiresAt =
            System.currentTimeMillis()
                + 300_000;


        redisTemplate
            .opsForZSet()
            .add(
                admittedKey(),
                USER_ID.toString(),
                expiresAt
            );


        AdmissionClaimResult claimResult =
            admissionClaimRepository
                .claim(
                    CONCERT_ID,
                    USER_ID
                );


        assertThat(claimResult.status())
            .isEqualTo(
                AdmissionClaimStatus
                    .CLAIMED
            );


        // when
        AdmissionRestoreStatus restoreStatus =
            admissionClaimRepository
                .restore(
                    CONCERT_ID,
                    USER_ID
                );


        // then
        assertThat(restoreStatus)
            .isEqualTo(
                AdmissionRestoreStatus
                    .RESTORED
            );


        assertThat(
            redisTemplate
                .opsForZSet()
                .score(
                    reservingKey(),
                    USER_ID.toString()
                )
        ).isNull();


        Double restoredScore =
            redisTemplate
                .opsForZSet()
                .score(
                    admittedKey(),
                    USER_ID.toString()
                );


        assertThat(restoredScore)
            .isEqualTo(
                (double) expiresAt
            );
    }


    @Test
    void reservation_실패_시점에_admission이_만료됐으면_복구하지_않는다() {

        // given
        long expiredAt =
            System.currentTimeMillis()
                - 1_000;


        redisTemplate
            .opsForZSet()
            .add(
                reservingKey(),
                USER_ID.toString(),
                expiredAt
            );


        // when
        AdmissionRestoreStatus result =
            admissionClaimRepository
                .restore(
                    CONCERT_ID,
                    USER_ID
                );


        // then
        assertThat(result)
            .isEqualTo(
                AdmissionRestoreStatus
                    .EXPIRED
            );


        assertThat(
            redisTemplate
                .opsForZSet()
                .score(
                    reservingKey(),
                    USER_ID.toString()
                )
        ).isNull();


        assertThat(
            redisTemplate
                .opsForZSet()
                .score(
                    admittedKey(),
                    USER_ID.toString()
                )
        ).isNull();
    }


    @Test
    void reserving이_아닌_사용자는_restore할_수_없다() {

        // when
        AdmissionRestoreStatus result =
            admissionClaimRepository
                .restore(
                    CONCERT_ID,
                    USER_ID
                );


        // then
        assertThat(result)
            .isEqualTo(
                AdmissionRestoreStatus
                    .NOT_RESERVING
            );
    }


    private String admittedKey() {

        return "queue:admitted:"
            + CONCERT_ID;
    }


    private String reservingKey() {

        return "queue:reserving:"
            + CONCERT_ID;
    }
}