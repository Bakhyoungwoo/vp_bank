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

    // redis 접근
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final List<String> CATEGORIES =
            List.of("economy", "society", "it", "politics", "world", "culture");

    // redis 연결 확인
    @PostConstruct
    public void checkConnection() {
        log.info("======= [REDIS CONNECTION CHECK] =======");
        try {
            String ping = redisTemplate.getConnectionFactory()
                    .getConnection().ping();
            log.info("Redis Ping: {}", ping);

            log.info("exists trend:it:scores = {}",
                    redisTemplate.hasKey("trend:it:scores"));
        } catch (Exception e) {
            log.error("Redis connection check failed", e);
        }
        log.info("========================================");
    }

    // 클릭 로그
    public void addClickLog(Long userId, List<String> keywords) {
        String key = "user:" + userId + ":keywords";

        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) continue;

            Double score = redisTemplate.opsForZSet()
                    .incrementScore(key, keyword.trim(), 1.0);

            log.info("[REDIS WRITE] key={}, keyword={}, score={}",
                    key, keyword, score);
        }

        redisTemplate.expire(key, Duration.ofDays(30));
    }


    // 상위 키워드
    public Set<String> getTopInterests(Long userId, int limit) {
        return redisTemplate.opsForZSet()
                .reverseRange("user:" + userId + ":keywords", 0, limit - 1);
    }

    // 최신 기사 조회
    public List<Map<String, Object>> getLatestArticles(
            String category, int limit) {

        String key = "trend:" + category.toLowerCase() + ":articles";
        List<String> rawArticles =
                redisTemplate.opsForList().range(key, 0, limit - 1);

        if (rawArticles == null || rawArticles.isEmpty()) {
            log.debug("No articles found. category={}", category);
            return new ArrayList<>();
        }

        return rawArticles.stream().map(json -> {
            try {
                return objectMapper.readValue(
                        json,
                        new TypeReference<Map<String, Object>>() {});
            } catch (JsonProcessingException e) {
                log.warn("Article JSON parse failed", e);
                Map<String, Object> error = new HashMap<>();
                error.put("error", "JSON 파싱 실패");
                return error;
            }
        }).collect(Collectors.toList());
    }

    // 사용자 행동기반 추천
    public List<Map<String, Object>> recommendArticles(
            Long userId, int limit) {

        Map<String, Double> userKeywordScore = new HashMap<>();

        Set<ZSetOperations.TypedTuple<String>> interests =
                redisTemplate.opsForZSet()
                        .reverseRangeWithScores(
                                "user:" + userId + ":keywords",
                                0, 19
                        );

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
                } catch (Exception ignored) {}
            }
        }

        List<Map<String, Object>> scored = new ArrayList<>();

        for (Map<String, Object> article : allArticles) {
            Object kwObj = article.get("keywords");
            if (!(kwObj instanceof Collection<?> col)) continue;

            double score = 0;
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
                scored.add(result);
            }
        }

        if (scored.isEmpty()) {
            log.info("[RECOMMEND] fallback to latest articles. userId={}", userId);
            return allArticles.stream()
                    .limit(limit)
                    .collect(Collectors.toList());
        }

        return scored.stream()
                .sorted((a, b) ->
                        Double.compare(
                                (double) b.get("score"),
                                (double) a.get("score")))
                .limit(limit)
                .collect(Collectors.toList());
    }

    // 키워드
    public List<Map<String, Object>> getTopKeywords(String category) {
        String key = "trend:" + category.toLowerCase() + ":scores";

        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet()
                        .reverseRangeWithScores(key, 0, 9);

        if (tuples == null || tuples.isEmpty()) {
            log.warn("[KEYWORD EMPTY] {}", key);
            return new ArrayList<>();
        }

        List<Map<String, Object>> result = new ArrayList<>();

        for (ZSetOperations.TypedTuple<String> t : tuples) {
            Map<String, Object> map = new HashMap<>();
            map.put("keyword", t.getValue());
            map.put("score", t.getScore().intValue());
            result.add(map);
        }

        return result;
    }

    // 크롤링 로직
    public boolean isCrawling(String category) {
        return Boolean.TRUE.equals(
                redisTemplate.hasKey("crawl:lock:" + category)
        );
    }

    public void markCrawling(String category) {
        redisTemplate.opsForValue()
                .set("crawl:lock:" + category, "1", Duration.ofMinutes(3));
    }

    public void clearCrawling(String category) {
        redisTemplate.delete("crawl:lock:" + category);
    }

    public void crawlAndSave(String category, List<Map<String, Object>> articles) {

        String key = "trend:" + category.toLowerCase() + ":articles";
        redisTemplate.delete(key);

        if (articles == null || articles.isEmpty()) return;

        for (Map<String, Object> article : articles) {
            try {
                redisTemplate.opsForList()
                        .rightPush(key, objectMapper.writeValueAsString(article));
            } catch (JsonProcessingException ignored) {}
        }

        redisTemplate.expire(key, Duration.ofMinutes(10));
    }
}
