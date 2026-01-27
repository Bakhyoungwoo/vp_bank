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

    private static final List<String> CATEGORIES =
            List.of("economy", "society", "it", "politics", "world", "culture");

    @PostConstruct
    public void checkConnection() {
        log.info("======= [REDIS CONNECTION CHECK] =======");
        try {
            // 간단한 핑 테스트
            String ping = Objects.requireNonNull(redisTemplate.getConnectionFactory())
                    .getConnection().ping();
            log.info("Redis Ping: {}", ping);
        } catch (Exception e) {
            log.error("Redis connection check failed", e);
        }
        log.info("========================================");
    }

    // 사용자 클릭 로그 저장 (비동기 처리 대상)
    public void addClickLog(Long userId, List<String> keywords) {
        // 키 이름 통일: user:{id}:keywords
        String key = "user:" + userId + ":keywords";

        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) continue;

            // 점수 1.0 증가
            Double score = redisTemplate.opsForZSet()
                    .incrementScore(key, keyword.trim(), 1.0);

            log.debug("[REDIS WRITE] key={}, keyword={}, newScore={}", key, keyword, score);
        }

        // 데이터 유효기간 30일 갱신
        redisTemplate.expire(key, Duration.ofDays(30));
    }

    // 사용자 관심 키워드 상위 조회
    public Set<String> getTopInterests(Long userId, int limit) {
        return redisTemplate.opsForZSet()
                .reverseRange("user:" + userId + ":keywords", 0, limit - 1);
    }

    // 카테고리별 최신 기사 조회 (단순 목록)
    public List<Map<String, Object>> getLatestArticles(String category, int limit) {
        String key = "trend:" + category.toLowerCase() + ":articles";
        List<String> rawArticles = redisTemplate.opsForList().range(key, 0, limit - 1);

        if (rawArticles == null || rawArticles.isEmpty()) {
            return new ArrayList<>();
        }

        return rawArticles.stream().map(this::parseJsonToMap)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // 사용자 개인화 추천 로직
    public List<Map<String, Object>> recommendArticles(Long userId, int limit) {

        // 1. 사용자 관심사 가져오기
        String userKey = "user:" + userId + ":keywords";
        Map<String, Double> userKeywordScore = new HashMap<>();

        Set<ZSetOperations.TypedTuple<String>> interests =
                redisTemplate.opsForZSet().reverseRangeWithScores(userKey, 0, 19);

        if (interests != null) {
            for (ZSetOperations.TypedTuple<String> t : interests) {
                if (t.getValue() != null) {
                    userKeywordScore.put(t.getValue(), t.getScore());
                }
            }
        }

        if (userKeywordScore.isEmpty()) {
            log.info("[RECOMMEND] 사용자 관심 키워드가 없습니다. (User ID: {})", userId);
            return getLatestArticles("it", limit);
        }

        // [디버깅 로그 1] 사용자가 가진 키워드 출력
        log.info("[DEBUG] User Interest Keywords: {}", userKeywordScore.keySet());

        // 2. 뉴스 기사 풀 가져오기
        List<Map<String, Object>> candidateArticles = new ArrayList<>();
        for (String category : CATEGORIES) {
            String key = "trend:" + category + ":articles";
            List<String> rawList = redisTemplate.opsForList().range(key, 0, 49);
            if (rawList != null) {
                for (String json : rawList) {
                    Map<String, Object> article = parseJsonToMap(json);
                    if (article != null) candidateArticles.add(article);
                }
            }
        }

        // 3. 매칭 점수 계산
        List<Map<String, Object>> scoredArticles = new ArrayList<>();

        for (Map<String, Object> article : candidateArticles) {
            Object kwObj = article.get("keywords");

            if (!(kwObj instanceof Collection<?> articleKeywords)) continue;

            double totalScore = 0.0;
            List<String> matched = new ArrayList<>();

            for (Object k : articleKeywords) {
                // 공백 제거 후 비교
                String keyword = String.valueOf(k).trim();

                // [디버깅 로그 2 - 너무 많으면 주석 처리]
                // log.debug("Comparing UserKW: {} vs ArticleKW: {}", userKeywordScore.keySet(), keyword);

                if (userKeywordScore.containsKey(keyword)) {
                    totalScore += userKeywordScore.get(keyword);
                    matched.add(keyword);
                }
            }

            if (totalScore > 0) {
                // [디버깅 로그 3] 매칭 성공 시 로그
                log.info("[MATCHED] Article: '{}', Keywords: {}", article.get("title"), matched);

                Map<String, Object> result = new HashMap<>(article);
                result.put("score", totalScore);
                result.put("matchedKeywords", matched);
                scoredArticles.add(result);
            }
        }

        // 4. 결과 반환
        if (scoredArticles.isEmpty()) {
            log.info("[RECOMMEND] No matched articles for user={}. Fallback to latest.", userId);
            return getLatestArticles("it", limit);
        }

        return scoredArticles.stream()
                .sorted((a, b) -> {
                    double scoreA = ((Number) a.get("score")).doubleValue();
                    double scoreB = ((Number) b.get("score")).doubleValue();
                    return Double.compare(scoreB, scoreA);
                })
                .limit(limit)
                .collect(Collectors.toList());
    }

    // 실시간 인기 트렌드 키워드 조회
    public List<Map<String, Object>> getTopKeywords(String category) {
        String key = "trend:" + category.toLowerCase() + ":scores";
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, 9);

        if (tuples == null || tuples.isEmpty()) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> t : tuples) {
            Map<String, Object> map = new HashMap<>();
            map.put("keyword", t.getValue());
            // Double -> Int 변환 (null 체크 포함)
            map.put("score", t.getScore() != null ? t.getScore().intValue() : 0);
            result.add(map);
        }
        return result;
    }

    // 크롤링 상태 관리 및 저장
    public boolean isCrawling(String category) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("crawl:lock:" + category));
    }

    public void markCrawling(String category) {
        redisTemplate.opsForValue().set("crawl:lock:" + category, "1", Duration.ofMinutes(3));
    }

    public void clearCrawling(String category) {
        redisTemplate.delete("crawl:lock:" + category);
    }

    public void crawlAndSave(String category, List<Map<String, Object>> articles) {
        String key = "trend:" + category.toLowerCase() + ":articles";

        // 기존 데이터 삭제
        redisTemplate.delete(key);

        if (articles == null || articles.isEmpty()) return;

        // Bulk Insert로 성능 최적화
        List<String> jsonList = new ArrayList<>();
        for (Map<String, Object> article : articles) {
            try {
                jsonList.add(objectMapper.writeValueAsString(article));
            } catch (JsonProcessingException e) {
                log.error("JSON write error", e);
            }
        }

        if (!jsonList.isEmpty()) {
            redisTemplate.opsForList().rightPushAll(key, jsonList);
            redisTemplate.expire(key, Duration.ofMinutes(10)); // 캐시 10분
        }
    }

    // JSON 파싱 헬퍼 메서드
    private Map<String, Object> parseJsonToMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("JSON parse failed: {}", json, e);
            return null;
        }
    }
}