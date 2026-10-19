package com.survive_the_traffic.queue.repository;

import com.survive_the_traffic.queue.entity.QueueStatus;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class QueueStatusRepository {

    private static final String WAITING_KEY_PREFIX =
        "queue:waiting:";

    private static final String ADMITTED_KEY_PREFIX =
        "queue:admitted:";

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List> queueStatusScript;

    public QueueStatusRepository(
        StringRedisTemplate redisTemplate,
        @Qualifier("queueStatusScript")
        RedisScript<List> queueStatusScript
    ) {
        this.redisTemplate = redisTemplate;
        this.queueStatusScript = queueStatusScript;
    }

    public QueueStatusResult getStatus(
        Long concertId,
        Long userId
    ) {

        long now =
            System.currentTimeMillis();

        List<?> result =
            redisTemplate.execute(
                queueStatusScript,
                List.of(
                    waitingKey(concertId),
                    admittedKey(concertId)
                ),
                userId.toString(),
                Long.toString(now)
            );

        if (result == null || result.size() != 3) {
            throw new IllegalStateException(
                "Queue status script returned invalid result"
            );
        }

        int statusCode =
            Integer.parseInt(
                result.get(0).toString()
            );

        long rawRank =
            Long.parseLong(
                result.get(1).toString()
            );

        long waitingCount =
            Long.parseLong(
                result.get(2).toString()
            );

        QueueStatus status =
            switch (statusCode) {
                case 0 -> QueueStatus.NOT_JOINED;
                case 1 -> QueueStatus.WAITING;
                case 2 -> QueueStatus.ADMITTED;
                default ->
                    throw new IllegalStateException(
                        "Unknown queue status code: "
                            + statusCode
                    );
            };

        Long rank =
            rawRank >= 0
                ? rawRank
                : null;

        return new QueueStatusResult(
            status,
            rank,
            waitingCount
        );
    }

    private String waitingKey(Long concertId) {
        return WAITING_KEY_PREFIX + concertId;
    }

    private String admittedKey(Long concertId) {
        return ADMITTED_KEY_PREFIX + concertId;
    }
}
