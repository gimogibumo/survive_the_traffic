package com.survive_the_traffic.queue.repository;

import com.survive_the_traffic.queue.dto.AdmissionClaimResult;
import com.survive_the_traffic.queue.entity.AdmissionClaimStatus;
import com.survive_the_traffic.queue.entity.AdmissionRestoreStatus;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class AdmissionClaimRepository {

    private static final String ADMITTED_KEY_PREFIX =
        "queue:admitted:";

    private static final String RESERVING_KEY_PREFIX =
        "queue:reserving:";


    private final StringRedisTemplate redisTemplate;

    private final RedisScript<List>
        claimAdmissionScript;

    private final RedisScript<Long>
        restoreAdmissionScript;


    public AdmissionClaimRepository(
        StringRedisTemplate redisTemplate,

        @Qualifier("claimAdmissionScript")
        RedisScript<List> claimAdmissionScript,

        @Qualifier("restoreAdmissionScript")
        RedisScript<Long> restoreAdmissionScript
    ) {

        this.redisTemplate =
            redisTemplate;

        this.claimAdmissionScript =
            claimAdmissionScript;

        this.restoreAdmissionScript =
            restoreAdmissionScript;
    }


    /**
     * 사용자의 admission을 원자적으로 점유한다.
     *
     * ADMITTED -> RESERVING
     *
     * @param concertId 공연 ID
     * @param userId    사용자 ID
     * @return claim 결과
     */
    public AdmissionClaimResult claim(
        Long concertId,
        Long userId
    ) {

        long now =
            System.currentTimeMillis();


        List<?> result =
            redisTemplate.execute(
                claimAdmissionScript,

                List.of(
                    admittedKey(
                        concertId
                    ),
                    reservingKey(
                        concertId
                    )
                ),

                userId.toString(),
                Long.toString(now)
            );


        if (
            result == null
                || result.size() != 2
        ) {

            throw new IllegalStateException(
                "Admission claim script returned invalid result"
            );
        }


        int statusCode =
            Integer.parseInt(
                result
                    .get(0)
                    .toString()
            );


        long rawExpiresAt =
            Long.parseLong(
                result
                    .get(1)
                    .toString()
            );


        AdmissionClaimStatus status =
            switch (statusCode) {

                case 0 ->
                    AdmissionClaimStatus
                        .NOT_ADMITTED;

                case 1 ->
                    AdmissionClaimStatus
                        .CLAIMED;

                case 2 ->
                    AdmissionClaimStatus
                        .ALREADY_RESERVING;

                default ->
                    throw new IllegalStateException(
                        "Unknown admission claim status code: "
                            + statusCode
                    );
            };


        Long expiresAt =
            rawExpiresAt >= 0
                ? rawExpiresAt
                : null;


        return new AdmissionClaimResult(
            status,
            expiresAt
        );
    }


    /**
     * Reservation이 성공했을 때 admission을 최종 소비한다.
     *
     * RESERVING -> CONSUMED
     *
     * 현재 CONSUMED를 별도 Redis 자료구조로 유지하지 않고,
     * RESERVING에서 제거된 상태 자체를 소비 완료로 본다.
     *
     * @return 실제로 RESERVING member가 제거되었으면 true
     */
    public boolean complete(
        Long concertId,
        Long userId
    ) {

        Long removed =
            redisTemplate
                .opsForZSet()
                .remove(
                    reservingKey(
                        concertId
                    ),
                    userId.toString()
                );


        return removed != null
            && removed > 0;
    }


    /**
     * Reservation이 실패했을 때 admission을 다시 복구한다.
     *
     * RESERVING -> ADMITTED
     *
     * 원래 expiration이 이미 지난 경우에는 복구하지 않는다.
     */
    public AdmissionRestoreStatus restore(
        Long concertId,
        Long userId
    ) {

        Long result =
            redisTemplate.execute(
                restoreAdmissionScript,

                List.of(
                    reservingKey(
                        concertId
                    ),
                    admittedKey(
                        concertId
                    )
                ),

                userId.toString(),

                Long.toString(
                    System.currentTimeMillis()
                )
            );


        if (result == null) {

            throw new IllegalStateException(
                "Restore admission script returned null"
            );
        }


        return switch (
            result.intValue()
            ) {

            case 0 ->
                AdmissionRestoreStatus
                    .NOT_RESERVING;

            case 1 ->
                AdmissionRestoreStatus
                    .RESTORED;

            case 2 ->
                AdmissionRestoreStatus
                    .EXPIRED;

            default ->
                throw new IllegalStateException(
                    "Unknown admission restore status code: "
                        + result
                );
        };
    }


    private String admittedKey(
        Long concertId
    ) {

        return ADMITTED_KEY_PREFIX
            + concertId;
    }


    private String reservingKey(
        Long concertId
    ) {

        return RESERVING_KEY_PREFIX
            + concertId;
    }
}