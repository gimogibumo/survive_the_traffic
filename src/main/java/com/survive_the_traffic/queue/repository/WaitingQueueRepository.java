package com.survive_the_traffic.queue.repository;

import com.survive_the_traffic.queue.dto.WaitingQueueJoinResult;
import com.survive_the_traffic.queue.entity.WaitingQueueJoinStatus;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class WaitingQueueRepository {

    private static final String ACTIVE_CONCERTS_KEY =
        "queue:active-concerts";

    private final StringRedisTemplate redisTemplate;

    private final DefaultRedisScript<List> joinWaitingQueueScript;


    public WaitingQueueRepository(
        StringRedisTemplate redisTemplate
    ) {
        this.redisTemplate =
            redisTemplate;

        this.joinWaitingQueueScript =
            new DefaultRedisScript<>();

        this.joinWaitingQueueScript.setLocation(
            new ClassPathResource(
                "redis/join-waiting-queue.lua"
            )
        );

        this.joinWaitingQueueScript.setResultType(
            List.class
        );
    }


    public WaitingQueueJoinResult join(
        Long concertId,
        Long userId
    ) {

        String sequenceKey =
            sequenceKey(
                concertId
            );

        String waitingKey =
            waitingKey(
                concertId
            );

        String admittedKey =
            admittedKey(
                concertId
            );


        List<?> result =
            redisTemplate.execute(
                joinWaitingQueueScript,
                List.of(
                    sequenceKey,
                    waitingKey,
                    ACTIVE_CONCERTS_KEY,
                    admittedKey
                ),
                userId.toString(),
                concertId.toString(),
                Long.toString(
                    System.currentTimeMillis()
                )
            );


        if (
            result == null
                || result.size() != 3
        ) {

            throw new IllegalStateException(
                "Invalid waiting queue join result"
            );
        }


        long statusCode =
            toLong(
                result.get(0)
            );

        long rank =
            toLong(
                result.get(1)
            );

        long waitingCount =
            toLong(
                result.get(2)
            );


        WaitingQueueJoinStatus status =
            switch (
                (int) statusCode
                ) {

                case 0 ->
                    WaitingQueueJoinStatus.JOINED;

                case 1 ->
                    WaitingQueueJoinStatus.ALREADY_WAITING;

                case 2 ->
                    WaitingQueueJoinStatus.ALREADY_ADMITTED;

                default ->
                    throw new IllegalStateException(
                        "Unknown waiting queue join status code: "
                            + statusCode
                    );
            };


        return new WaitingQueueJoinResult(
            status,
            rank,
            waitingCount
        );
    }


    public Long getRank(
        Long concertId,
        Long userId
    ) {

        return redisTemplate
            .opsForZSet()
            .rank(
                waitingKey(
                    concertId
                ),
                userId.toString()
            );
    }


    public long getSize(
        Long concertId
    ) {

        Long size =
            redisTemplate
                .opsForZSet()
                .zCard(
                    waitingKey(
                        concertId
                    )
                );


        if (size == null) {
            return 0L;
        }


        return size;
    }


    public List<Long> popFirst(
        Long concertId,
        long count
    ) {

        if (count <= 0) {
            return Collections.emptyList();
        }


        Set<TypedTuple<String>> popped =
            redisTemplate
                .opsForZSet()
                .popMin(
                    waitingKey(
                        concertId
                    ),
                    count
                );


        if (
            popped == null
                || popped.isEmpty()
        ) {

            return Collections.emptyList();
        }


        return popped.stream()
            .map(
                ZSetOperations.TypedTuple::getValue
            )
            .filter(
                value ->
                    value != null
            )
            .map(
                Long::valueOf
            )
            .toList();
    }


    private long toLong(
        Object value
    ) {

        if (value == null) {

            throw new IllegalStateException(
                "Lua result contains null"
            );
        }


        if (value instanceof Number number) {

            return number.longValue();
        }


        return Long.parseLong(
            value.toString()
        );
    }


    private String waitingKey(
        Long concertId
    ) {

        return "queue:waiting:"
            + concertId;
    }


    private String sequenceKey(
        Long concertId
    ) {

        return "queue:sequence:"
            + concertId;
    }


    private String admittedKey(
        Long concertId
    ) {

        return "queue:admitted:"
            + concertId;
    }
}
