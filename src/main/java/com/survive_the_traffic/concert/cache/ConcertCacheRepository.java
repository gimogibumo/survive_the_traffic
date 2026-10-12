package com.survive_the_traffic.concert.cache;

import com.survive_the_traffic.concert.dto.ConcertResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConcertCacheRepository {

    private static final String KEY_PREFIX = "concert:";

    private final RedisTemplate<String, Object> redisTemplate;

    public ConcertResponse findById(Long concertId) {
        System.out.println("REDIS GET: " + KEY_PREFIX + concertId);
        Object cached =
            redisTemplate.opsForValue().get(KEY_PREFIX + concertId);

        System.out.println("CACHED: " + cached);

        if(cached == null) {
            return null;
        }

        return (ConcertResponse) cached;
    }

    public void save(Long concertId, ConcertResponse response) {
        System.out.println("REDIS SAVE: " + KEY_PREFIX + concertId);
        System.out.println("REDIS VALUE: " + response);
        redisTemplate.opsForValue().set(KEY_PREFIX + concertId, response);

    }

}
