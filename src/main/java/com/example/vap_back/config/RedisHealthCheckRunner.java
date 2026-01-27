package com.example.vap_back.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisHealthCheckRunner implements CommandLineRunner {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void run(String... args) {
        try {
            String ping = redisTemplate.getConnectionFactory()
                    .getConnection()
                    .ping();

            log.info("[REDIS CHECK] ping = {}", ping);

            Boolean exists = redisTemplate.hasKey("trend:it:scores");
            log.info("[REDIS CHECK] exists trend:it:scores = {}", exists);

            if (Boolean.TRUE.equals(exists)) {
                Long size = redisTemplate.opsForZSet()
                        .size("trend:it:scores");
                log.info("[REDIS CHECK] trend:it:scores size = {}", size);
            }

        } catch (Exception e) {
            log.error("[REDIS CHECK] ❌ Redis connection failed", e);
        }
    }
}
