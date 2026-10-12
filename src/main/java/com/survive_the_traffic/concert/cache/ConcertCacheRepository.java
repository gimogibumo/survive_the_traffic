package com.survive_the_traffic.concert.cache;

import com.survive_the_traffic.concert.dto.CacheStatus;
import com.survive_the_traffic.concert.dto.ConcertCacheResult;
import com.survive_the_traffic.concert.dto.ConcertResponse;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConcertCacheRepository {

    private static final String KEY_PREFIX = "concert:";
    private static final String NOT_FOUND_VALUE = "__NOT_FOUND__";

    private final RedisTemplate<String, Object> redisTemplate;

    public ConcertCacheResult findById(Long concertId) {
        try {
            Object cached =
                redisTemplate.opsForValue().get(KEY_PREFIX + concertId);

            if (cached == null) {
                return new ConcertCacheResult(CacheStatus.MISS,null);
            }

            if(NOT_FOUND_VALUE.equals(cached)) {
                return new ConcertCacheResult(CacheStatus.NOT_FOUND, null);
            }

            return new ConcertCacheResult(CacheStatus.HIT, (ConcertResponse) cached);

        } catch (DataAccessException e) {
            log.warn(
                "Redis read failed. concertId={}",
                concertId,
                e
            );
            return new ConcertCacheResult(CacheStatus.MISS, null);
        }
    }

    public void save(Long concertId, ConcertResponse response) {
        try {
            redisTemplate.opsForValue()
                .set(KEY_PREFIX + concertId, response, Duration.ofMinutes(10));
        } catch (DataAccessException e) {

            log.warn(
                "Redis write failed. concertId={}",
                concertId,
                e
            );
        }
    }

    public void delete(Long concertId) {
        try {
            redisTemplate.delete(KEY_PREFIX + concertId);
        } catch (DataAccessException e) {
            log.warn(
                "Redis cache eviction failed. concertId={}",
                concertId,
                e
            );
        }
    }

    public void saveNotFound(Long concertId) {
        try {
            redisTemplate.opsForValue().set(
                KEY_PREFIX + concertId,
                NOT_FOUND_VALUE,
                Duration.ofSeconds(30)
            );
        } catch (DataAccessException e) {
            log.warn(
                "Redis negative cache write failed. concertId={}",
                concertId,
                e
            );
        }
    }
}
