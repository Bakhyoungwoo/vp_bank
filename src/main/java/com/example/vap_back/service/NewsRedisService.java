package com.example.vap_back.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
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
        log.info("======= [REDIS CONNECTION CHECK] =======");
        try {
            String ping = redisTemplate.getConnectionFactory()
                    .getConnection().ping();
            log.info("Redis Ping: {}", ping);

            for (String category : CATEGORIES) {
                String key = "trend:" + category + ":articles";
                Long size = redisTemplate.opsForList().size(key);
                log.debug("Redis key={} size={}", key, size);
            }
        } catch (Exception e) {
            log.error("Redis connection check failed", e);
        }
        log.info("========================================");
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

        log.debug("[REDIS] Click log saved. userId={}, keywords={}",
                userId, keywords);
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

        if (rawArticles == null) {
            log.debug("No articles found in Redis. category={}", category);
            return List.of();
        }

        return rawArticles.stream().map(json -> {
            try {
                return objectMapper.readValue(
                        json,
                        new TypeReference<Map<String, Object>>() {});
            } catch (JsonProcessingException e) {
                log.warn("Article JSON parse failed. category={}", category, e);
                Map<String, Object> error = new HashMap<>();
                error.put("error", "JSON 파싱 실패");
                return error;
            }
        }).collect(Collectors.toList());
    }

    // 사용자 행동 기반 추천 (핵심)
    public List<Map<String, Object>> recommendArticles(
            Long userId, int limit) {

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
                } catch (Exception e) {
                    log.debug("Skip invalid article JSON. category={}", category);
                }
            }
        }

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

        List<Map<String, Object>> result = scoredArticles.stream()
                .sorted((a, b) ->
                        Double.compare(
                                (double) b.get("score"),
                                (double) a.get("score")
                        ))
                .limit(limit)
                .collect(Collectors.toList());

        if (result.isEmpty()) {
            log.info("[RECOMMEND] fallback to latest articles. userId={}", userId);
            return allArticles.stream()
                    .limit(limit)
                    .collect(Collectors.toList());
        }

        return result;
    }

    // 크롤링 중인지 확인
    public boolean isCrawling(String category) {
        return Boolean.TRUE.equals(
                redisTemplate.hasKey("crawl:lock:" + category)
        );
    }

    // 크롤링 시작 표시
    public void markCrawling(String category) {
        redisTemplate.opsForValue()
                .set("crawl:lock:" + category, "1", Duration.ofMinutes(3));
        log.debug("Crawling lock set. category={}", category);
    }

    // 크롤링 종료
    public void clearCrawling(String category) {
        redisTemplate.delete("crawl:lock:" + category);
        log.debug("Crawling lock cleared. category={}", category);
    }

    public void crawlAndSave(String category, List<Map<String, Object>> articles) {

        String key = "trend:" + category.toLowerCase() + ":articles";

        // 기존 기사 삭제
        redisTemplate.delete(key);

        if (articles == null || articles.isEmpty()) {
            log.warn("No articles to save. category={}", category);
            return;
        }

        // 최신 기사 저장
        for (Map<String, Object> article : articles) {
            try {
                String json = objectMapper.writeValueAsString(article);
                redisTemplate.opsForList().rightPush(key, json);
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize article. category={}", category, e);
            }
        }

        // TTL 설정 (예: 10분)
        redisTemplate.expire(key, Duration.ofMinutes(10));

        log.info("Articles saved to Redis. category={}, count={}",
                category, articles.size());
    }
}
