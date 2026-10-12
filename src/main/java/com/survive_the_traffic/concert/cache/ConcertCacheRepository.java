package com.survive_the_traffic.concert.cache;

import com.survive_the_traffic.concert.dto.ConcertResponse;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConcertCacheRepository {

    private static final String KEY_PREFIX = "concert:";

    private final RedisTemplate<String, Object> redisTemplate;

    public ConcertResponse findById(Long concertId) {
        Object cached =
            redisTemplate.opsForValue().get(KEY_PREFIX + concertId);


        if(cached == null) {
            return null;
        }

        return (ConcertResponse) cached;
    }

    public void save(Long concertId, ConcertResponse response) {
        redisTemplate.opsForValue().set(KEY_PREFIX + concertId, response, Duration.ofSeconds(10));

    }

    public void delete(Long concertId) {
        redisTemplate.delete(KEY_PREFIX + concertId);
    }
}
