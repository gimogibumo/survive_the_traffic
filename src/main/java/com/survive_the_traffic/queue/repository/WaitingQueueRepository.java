package com.survive_the_traffic.queue.repository;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class WaitingQueueRepository {

    private static final String WAITING_KEY_PREFIX =
        "queue:waiting:";

    private static final String SEQUENCE_KEY_PREFIX =
        "queue:sequence:";

    private static final String ACTIVE_CONCERTS_KEY =
        "queue:active-concerts";

    private final StringRedisTemplate redisTemplate;

    @Qualifier("joinWaitingQueueScript")
    private final RedisScript<List> joinWaitingQueueScript;

    private String waitingKey(Long concertId) {
        return WAITING_KEY_PREFIX + concertId;
    }

    private String sequenceKey(Long concertId) {
        return SEQUENCE_KEY_PREFIX + concertId;
    }

    public WaitingQueueJoinResult join(
        Long concertId,
        Long userId
    ) {

        List<?> result = redisTemplate.execute(
            joinWaitingQueueScript,
            List.of(
                sequenceKey(concertId),
                waitingKey(concertId),
                ACTIVE_CONCERTS_KEY
            ),
            userId.toString(),
            concertId.toString()
        );

        if (result == null || result.isEmpty()) {
            throw new IllegalStateException(
                "Waiting queue join script returned no result"
            );
        }

        boolean added =
            Long.parseLong(
                result.get(0).toString()
            ) == 1L;

        long rank =
            Long.parseLong(
                result.get(1).toString()
            );

        long waitingCount =
            Long.parseLong(
                result.get(2).toString()
            );

        return new WaitingQueueJoinResult(added,rank,waitingCount);
    }

    public Long getRank(Long concertId, Long userId) {
        return redisTemplate.opsForZSet()
            .rank(waitingKey(concertId),userId.toString());
    }

    public Long getSize(Long concertId) {
        return redisTemplate.opsForZSet().size(waitingKey(concertId));
    }

    public List<Long> popFirst(Long concertId, Long count) {

        var tuples = redisTemplate.opsForZSet()
            .popMin(waitingKey(concertId),count);

        if(tuples == null) {
            return List.of();
        }

        return tuples.stream()
            .map(tuple -> Long.valueOf(tuple.getValue()))
            .toList();
    }
}
