package com.survive_the_traffic.common.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
public class RedisScriptConfig {

    @Bean("admitUsersScript")
    public RedisScript<List> redisScript() {
        return RedisScript.of(
            new ClassPathResource("redis/admit-users.lua"),
            List.class
        );
    }

    @Bean("joinWaitingQueueScript")
    public RedisScript<List> joinWaitingQueueScript() {
        return RedisScript.of(
            new ClassPathResource(
                "redis/join-waiting-queue.lua"
            ),
            List.class
        );
    }

    @Bean("queueStatusScript")
    public RedisScript<List> queueStatusScript() {
        return RedisScript.of(
            new ClassPathResource(
                "redis/queue-status.lua"
            ),
            List.class
        );
    }



}
