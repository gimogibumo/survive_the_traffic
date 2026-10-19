package com.survive_the_traffic.queue.repository;

import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ActiveConcertRepository {

    private static final String ACTIVE_CONCERT_KEY =
        "queue:active-concerts";

    private final StringRedisTemplate redisTemplate;

    public boolean activate(Long concertId) {

        Long added = redisTemplate.opsForSet().add(ACTIVE_CONCERT_KEY,concertId.toString());
        return added != null && added > 0;
    }

    public Set<Long> findAll() {

        Set<String> members = redisTemplate.opsForSet().members(ACTIVE_CONCERT_KEY);

        if(members == null || members.isEmpty()) {
            return Set.of();
        }

        return members.stream()
            .map(Long::valueOf)
            .collect(Collectors.toSet());
    }
}
