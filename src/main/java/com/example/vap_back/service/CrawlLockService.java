package com.example.vap_back.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class CrawlLockService {

    private final StringRedisTemplate redisTemplate;

    private static final String LOCK_KEY_PREFIX = "crawl:lock:";
    private static final Duration LOCK_TTL = Duration.ofMinutes(3);

    public boolean isLocked(String category) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(LOCK_KEY_PREFIX + category));
    }

    public void lock(String category) {
        redisTemplate.opsForValue().set(LOCK_KEY_PREFIX + category, "1", LOCK_TTL);
    }

    public void unlock(String category) {
        redisTemplate.delete(LOCK_KEY_PREFIX + category);
    }
}
