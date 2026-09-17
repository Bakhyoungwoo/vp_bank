package com.example.vap_back.service.impl;

import com.example.vap_back.service.NewsTrendingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class NewsTrendingServiceImpl implements NewsTrendingService {

    private final StringRedisTemplate redisTemplate;

    private static final int TOP_KEYWORDS = 10;

    @Override
    public List<Map<String, Object>> getTopKeywords(String category) {
        String key = "trend:" + category.toLowerCase() + ":scores";
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, TOP_KEYWORDS - 1);

        if (tuples == null || tuples.isEmpty()) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> t : tuples) {
            Map<String, Object> map = new HashMap<>();
            map.put("keyword", t.getValue());
            map.put("score", t.getScore() != null ? t.getScore().intValue() : 0);
            result.add(map);
        }
        return result;
    }
}
