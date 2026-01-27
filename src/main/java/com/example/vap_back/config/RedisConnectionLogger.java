package com.example.vap_back.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisConnectionLogger {

    private final RedisConnectionFactory connectionFactory;

    @PostConstruct
    public void logRedisConnection() {
        log.info("[REDIS CONFIG] RedisConnectionFactory = {}",
                connectionFactory.getClass().getName());

        log.info("[REDIS CONFIG] Redis connection factory detail = {}",
                connectionFactory);
    }
}
