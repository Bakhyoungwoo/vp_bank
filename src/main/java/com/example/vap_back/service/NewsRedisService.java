package com.example.vap_back.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class NewsRedisService {

    private final RedisTemplate<String, String> redisTemplate;
    public NewsRedisService(
            @Qualifier("newsRedisTemplate")
            RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    // key: trend:{category}:scores
    public List<Map<String, Object>> getTrendKeywords(String category) {
        String key = "trend:" + category + ":scores";

        Set<ZSetOperations.TypedTuple<String>> range =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, 4);

        if (range == null || range.isEmpty()) {
            System.out.println("[Redis] No keyword scores for key = " + key);
            return List.of();
        }

        return range.stream()
                .map(tuple -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("keyword", tuple.getValue());
                    map.put("score", tuple.getScore());
                    return map;
                })
                .collect(Collectors.toList());
    }

    // key: trend:{category}:articles
    public List<String> getLatestArticles(String category, int limit) {
        String key = "trend:" + category + ":articles";

        List<String> articles =
                redisTemplate.opsForList().range(key, 0, limit - 1);

        if (articles == null || articles.isEmpty()) {
            System.out.println("[Redis] No articles for key = " + key);
            return List.of();
        }

        return articles;
    }
}
