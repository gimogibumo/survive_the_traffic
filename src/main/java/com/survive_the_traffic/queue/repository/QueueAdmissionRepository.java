package com.survive_the_traffic.queue.repository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class QueueAdmissionRepository {

    private static final String ADMITTED_KEY_PREFIX =
        "queue:admitted:";

    private static final String WAITING_KEY_PREFIX =
        "queue:waiting:";

    private static final Duration ADMISSION_TTL =
        Duration.ofMinutes(5);


    private final StringRedisTemplate redisTemplate;

    private final RedisScript<List> admitUsersScript;


    public List<Long> admit(
        Long concertId,
        long count
    ) {

        if (count <= 0) {

            throw new IllegalArgumentException(
                "count must be greater than 0"
            );
        }


        /*
         * 동일한 기준 시각으로
         *
         * now
         * expiresAt
         *
         * 을 계산한다.
         *
         * Lua script에서는 now 이하의 기존 admission을
         * expired member로 판단한다.
         */
        long now =
            Instant.now()
                .toEpochMilli();


        long expiresAt =
            now
                + ADMISSION_TTL.toMillis();


        List<?> result =
            redisTemplate.execute(
                admitUsersScript,
                List.of(
                    waitingKey(
                        concertId
                    ),
                    admittedKey(
                        concertId
                    )
                ),
                Long.toString(
                    count
                ),
                Long.toString(
                    expiresAt
                ),
                Long.toString(
                    now
                )
            );


        if (
            result == null
                || result.isEmpty()
        ) {

            return List.of();
        }


        return result.stream()
            .map(
                Object::toString
            )
            .map(
                Long::valueOf
            )
            .toList();
    }


    private String waitingKey(
        Long concertId
    ) {

        return WAITING_KEY_PREFIX
            + concertId;
    }


    private String admittedKey(
        Long concertId
    ) {

        return ADMITTED_KEY_PREFIX
            + concertId;
    }
}