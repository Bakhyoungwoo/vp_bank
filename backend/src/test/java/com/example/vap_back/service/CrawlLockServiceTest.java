package com.example.vap_back.service;

import com.example.vap_back.service.impl.CrawlLockServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class CrawlLockServiceTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    @InjectMocks
    CrawlLockServiceImpl crawlLockService;

    @Test
    @DisplayName("잠금 확인 - Redis에 키가 있으면 true 반환")
    void isLocked_keyExists_returnsTrue() {
        // given
        given(redisTemplate.hasKey("crawl:lock:economy")).willReturn(true);

        // when
        boolean result = crawlLockService.isLocked("economy");

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("잠금 확인 - Redis에 키가 없으면 false 반환")
    void isLocked_keyAbsent_returnsFalse() {
        // given
        given(redisTemplate.hasKey("crawl:lock:economy")).willReturn(false);

        // when
        boolean result = crawlLockService.isLocked("economy");

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("잠금 설정 - 3분 TTL로 Redis에 키 저장")
    void lock_setsKeyWithTtl() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOps);

        // when
        crawlLockService.lock("economy");

        // then
        then(valueOps).should().set(
                eq("crawl:lock:economy"),
                eq("1"),
                eq(Duration.ofMinutes(3)));
    }

    @Test
    @DisplayName("잠금 해제 - Redis에서 키 삭제")
    void unlock_deletesKey() {
        // when
        crawlLockService.unlock("economy");

        // then
        then(redisTemplate).should().delete("crawl:lock:economy");
    }
}
