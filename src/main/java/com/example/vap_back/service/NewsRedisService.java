package com.example.vap_back.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NewsRedisService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // 명시적 카테고리
    private static final List<String> CATEGORIES =
            List.of("economy", "society", "it");

    // Redis 연결 확인
    @PostConstruct
    public void checkConnection() {
        System.out.println("======= [REDIS CONNECTION DEBUG] =======");
        try {
            System.out.println("Redis Ping: " +
                    redisTemplate.getConnectionFactory()
                            .getConnection().ping());

            for (String category : CATEGORIES) {
                String key = "trend:" + category + ":articles";
                Long size = redisTemplate.opsForList().size(key);
                System.out.println(key + " size = " + size);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("========================================");
    }

    // 클릭 로그 저장
    public void addClickLog(Long userId, List<String> keywords) {
        String key = "user:" + userId + ":interests";

        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) continue;
            redisTemplate.opsForZSet()
                    .incrementScore(key, keyword.trim(), 1.0);
        }

        redisTemplate.expire(key, Duration.ofDays(30));

        System.out.println("[REDIS] Click Log Saved for User "
                + userId + " : " + keywords);
    }

    // 유저 상위 관심 키워드
    public Set<String> getTopInterests(Long userId, int limit) {
        return redisTemplate.opsForZSet()
                .reverseRange("user:" + userId + ":interests", 0, limit - 1);
    }

    // 최신 기사 조회
    public List<Map<String, Object>> getLatestArticles(
            String category, int limit) {

        String key = "trend:" + category.toLowerCase() + ":articles";
        List<String> rawArticles =
                redisTemplate.opsForList().range(key, 0, limit - 1);

        if (rawArticles == null) return List.of();

        return rawArticles.stream().map(json -> {
            try {
                return objectMapper.readValue(
                        json,
                        new TypeReference<Map<String, Object>>() {});
            } catch (JsonProcessingException e) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "JSON 파싱 실패");
                return error;
            }
        }).collect(Collectors.toList());
    }

    // 사용자 행동 기반 추천 (핵심)
    public List<Map<String, Object>> recommendArticles(
            Long userId, int limit) {

        // 관심 키워드
        Map<String, Double> userKeywordScore = new HashMap<>();

        Set<ZSetOperations.TypedTuple<String>> interests =
                redisTemplate.opsForZSet()
                        .reverseRangeWithScores(
                                "user:" + userId + ":interests", 0, 19);

        if (interests != null) {
            for (ZSetOperations.TypedTuple<String> t : interests) {
                if (t.getValue() != null && t.getScore() != null) {
                    userKeywordScore.put(t.getValue(), t.getScore());
                }
            }
        }

        // 기사 수집
        List<Map<String, Object>> allArticles = new ArrayList<>();

        for (String category : CATEGORIES) {
            String key = "trend:" + category + ":articles";
            List<String> rawList =
                    redisTemplate.opsForList().range(key, 0, 50);

            if (rawList == null) continue;

            for (String json : rawList) {
                try {
                    Map<String, Object> article =
                            objectMapper.readValue(
                                    json,
                                    new TypeReference<Map<String, Object>>() {});
                    allArticles.add(article);
                } catch (Exception ignored) {}
            }
        }

        // 기사별 점수 계산
        List<Map<String, Object>> scoredArticles = new ArrayList<>();

        for (Map<String, Object> article : allArticles) {

            Object kwObj = article.get("keywords");
            if (!(kwObj instanceof Collection<?> col)) continue;

            double score = 0.0;
            List<String> matched = new ArrayList<>();

            for (Object o : col) {
                String kw = String.valueOf(o);
                Double weight = userKeywordScore.get(kw);
                if (weight != null) {
                    score += weight;
                    matched.add(kw);
                }
            }

            if (score > 0) {
                Map<String, Object> result = new HashMap<>(article);
                result.put("score", score);
                result.put("matchedKeywords", matched);
                scoredArticles.add(result);
            }
        }

        // 점수 기준 정렬
        List<Map<String, Object>> result = scoredArticles.stream()
                .sorted((a, b) ->
                        Double.compare(
                                (double) b.get("score"),
                                (double) a.get("score")
                        ))
                .limit(limit)
                .collect(Collectors.toList());

        // fallback을 이용한 빈배열 방지
        if (result.isEmpty()) {
            System.out.println("[RECOMMEND] fallback to latest articles");
            return allArticles.stream()
                    .limit(limit)
                    .collect(Collectors.toList());
        }

        return result;
    }
}
