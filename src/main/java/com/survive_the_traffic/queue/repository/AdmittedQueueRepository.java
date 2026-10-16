package com.survive_the_traffic.queue.repository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdmittedQueueRepository {

    private static final String ADMITTED_KEY_PREFIX = "queue:admitted:";

    private static final Duration ADMISSION_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;

    private final QueueAdmissionRepository queueAdmissionRepository;

    private String admittedKey(Long concertId) {
        return ADMITTED_KEY_PREFIX + concertId;
    }

    public void add(Long concertId, List<Long> userIds) {
        long expiresAt = Instant.now().plus(ADMISSION_TTL).toEpochMilli();
        String key = admittedKey(concertId);
        for(Long userId : userIds) {
            redisTemplate.opsForZSet()
                .add(key,userId.toString(),expiresAt);
        }
    }

    public boolean isAdmitted(Long concertId, Long userId) {
        Double expiresAt = redisTemplate.opsForZSet()
            .score(
                admittedKey(concertId),
                userId.toString()
            );

        if(expiresAt == null) {
            return false;
        }

        return expiresAt > System.currentTimeMillis();
    }

    public Long getSize(Long concertId) {
        return redisTemplate.opsForZSet().size(admittedKey(concertId));
    }
}
