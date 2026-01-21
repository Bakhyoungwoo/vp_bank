package com.example.vap_back.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NewsRedisService {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 특정 카테고리의 실시간 트렌드 키워드 조회 (Score 순)
     */
    public List<Map<String, Object>> getTrendKeywords(String category) {
        String key = "trend:" + category + ":scores";

        // Redis ZSET에서 상위 5개 가져오기
        Set<Object> range = redisTemplate.opsForZSet().reverseRange(key, 0, 4);

        return range.stream().map(word -> {
            Double score = redisTemplate.opsForZSet().score(key, word);
            return Map.of("keyword", word, "score", score);
        }).collect(Collectors.toList());
    }

    /**
     * 특정 카테고리의 최신 기사 리스트 조회
     * (FastAPI에서 trend:category:articles 키에 리스트를 저장했을 경우)
     */
    public List<Object> getLatestArticles(String category) {
        String key = "trend:" + category + ":articles";
        return redisTemplate.opsForList().range(key, 0, 9);
    }
}