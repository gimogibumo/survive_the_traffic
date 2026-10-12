package com.survive_the_traffic.common.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisConnectChecker {

    private final RedisConnectionFactory redisConnectionFactory;

    @PostConstruct
    public void checkRedisConnection() {

        if (redisConnectionFactory instanceof LettuceConnectionFactory factory) {

            System.out.println("===== REDIS CONNECTION =====");
            System.out.println("HOST = " + factory.getHostName());
            System.out.println("PORT = " + factory.getPort());
            System.out.println("DB   = " + factory.getDatabase());
            System.out.println("============================");
        }
    }
}
