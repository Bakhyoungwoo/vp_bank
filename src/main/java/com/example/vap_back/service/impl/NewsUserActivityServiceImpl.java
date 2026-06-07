package com.example.vap_back.service.impl;

import com.example.vap_back.service.NewsUserActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
public class NewsUserActivityServiceImpl implements NewsUserActivityService {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void addClickLog(Long userId, List<String> keywords) {
        String key = "user:" + userId + ":keywords";
        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) continue;
            redisTemplate.opsForZSet().incrementScore(key, keyword.trim(), 1.0);
        }
        redisTemplate.expire(key, Duration.ofDays(30));
    }

    @Override
    public Set<String> getTopInterests(Long userId, int limit) {
        return redisTemplate.opsForZSet()
                .reverseRange("user:" + userId + ":keywords", 0, limit - 1);
    }

    @Override
    public Map<String, Double> getTopInterestsWithScores(Long userId, int limit) {
        Set<ZSetOperations.TypedTuple<String>> interests =
                redisTemplate.opsForZSet().reverseRangeWithScores("user:" + userId + ":keywords", 0, limit - 1);

        Map<String, Double> result = new HashMap<>();
        if (interests != null) {
            for (ZSetOperations.TypedTuple<String> t : interests) {
                if (t.getValue() != null) {
                    result.put(t.getValue(), t.getScore());
                }
            }
        }
        return result;
    }
}
