package com.survive_the_traffic.queue.repository;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class WaitingQueueRepository {

    private static final String WAITING_KEY_PREFIX = "queue:waiting:";
    private static final String SEQUENCE_KEY_PREFIX = "queue:sequence:";

    private final StringRedisTemplate redisTemplate;

    private String waitingKey(Long concertId) {
        return WAITING_KEY_PREFIX + concertId;
    }

    private String sequenceKey(Long concertId) {
        return SEQUENCE_KEY_PREFIX + concertId;
    }

    public boolean join(Long concertId, Long userId) {
        Long sequence = redisTemplate.opsForValue().increment(sequenceKey(concertId));
        Boolean added = redisTemplate.opsForZSet()
            .addIfAbsent(waitingKey(concertId),userId.toString(),sequence.doubleValue());

        return Boolean.TRUE.equals(added);
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
