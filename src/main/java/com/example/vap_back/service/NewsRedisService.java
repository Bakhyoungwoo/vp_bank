package com.example.vap_back.service;

import com.example.vap_back.Entity.News;
import com.example.vap_back.repository.NewsRepository;
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
    private final NewsRepository newsRepository; // 🔥 DB 접근을 위해 추가

    private static final List<String> CATEGORIES =
            List.of("economy", "society", "it", "politics", "world", "culture");

    @PostConstruct
    public void checkConnection() {
        log.info("======= [REDIS CONNECTION CHECK] =======");
        try {
            String ping = Objects.requireNonNull(redisTemplate.getConnectionFactory())
                    .getConnection().ping();
            log.info("Redis Ping: {}", ping);
        } catch (Exception e) {
            log.error("Redis connection check failed", e);
        }
        log.info("========================================");
    }

    // 사용자 클릭 로그 저장
    public void addClickLog(Long userId, List<String> keywords) {
        String key = "user:" + userId + ":keywords";

        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) continue;
            redisTemplate.opsForZSet().incrementScore(key, keyword.trim(), 1.0);
        }
        redisTemplate.expire(key, Duration.ofDays(30));
    }

    // 사용자 관심 키워드 상위 조회
    public Set<String> getTopInterests(Long userId, int limit) {
        return redisTemplate.opsForZSet()
                .reverseRange("user:" + userId + ":keywords", 0, limit - 1);
    }

    // cashing aside
    public List<Map<String, Object>> getLatestArticles(String category, int limit) {
        String key = "trend:" + category.toLowerCase() + ":articles";

        // Redis 조회
        List<String> rawArticles = redisTemplate.opsForList().range(key, 0, limit - 1);

        if (rawArticles != null && !rawArticles.isEmpty()) {
            log.debug("[CACHE HIT] Redis에서 {} 뉴스 조회", category);
            return rawArticles.stream()
                    .map(this::parseJsonToMap)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        // Redis Miss시에 DB 조회
        log.info("[CACHE MISS] DB에서 {} 뉴스 조회 및 캐싱 시도", category);

        // DB에서 최신순 50개
        List<News> dbNewsList = newsRepository.findTop50ByCategoryOrderByPublishedAtDesc(category);

        if (dbNewsList.isEmpty()) {
            return new ArrayList<>();
        }

        // DB 데이터를 Redis에 캐싱
        List<Map<String, Object>> resultList = new ArrayList<>();
        List<String> jsonList = new ArrayList<>();

        for (News news : dbNewsList) {
            Map<String, Object> map = convertEntityToMap(news);
            resultList.add(map);
            try {
                jsonList.add(objectMapper.writeValueAsString(map));
            } catch (JsonProcessingException e) {
                log.error("JSON 변환 에러", e);
            }
        }

        if (!jsonList.isEmpty()) {
            // 기존 키 삭제 후 새로 캐싱
            redisTemplate.delete(key);
            redisTemplate.opsForList().rightPushAll(key, jsonList);
            redisTemplate.expire(key, Duration.ofMinutes(10)); // 10분 TTL
        }

        // 요청한 limit 만큼 반환
        return resultList.stream().limit(limit).collect(Collectors.toList());
    }

    // 사용자 개인화 추천 로직
    public List<Map<String, Object>> recommendArticles(Long userId, int limit) {

        // 사용자 관심사 가져오기
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
            return getLatestArticles("it", limit);
        }

        // 뉴스 기사 풀 가져오기 (Redis에 없으면 DB에서 가져옴)
        List<Map<String, Object>> candidateArticles = new ArrayList<>();
        for (String category : CATEGORIES) {
            List<Map<String, Object>> articles = getLatestArticles(category, 50);
            candidateArticles.addAll(articles);
        }

        // 매칭 점수 계산
        List<Map<String, Object>> scoredArticles = new ArrayList<>();

        for (Map<String, Object> article : candidateArticles) {
            Object kwObj = article.get("keywords");

            if (!(kwObj instanceof Collection<?> articleKeywords)) continue;

            double totalScore = 0.0;
            List<String> matched = new ArrayList<>();

            for (Object k : articleKeywords) {
                String keyword = String.valueOf(k).trim();
                if (userKeywordScore.containsKey(keyword)) {
                    totalScore += userKeywordScore.get(keyword);
                    matched.add(keyword);
                }
            }

            if (totalScore > 0) {
                Map<String, Object> result = new HashMap<>(article);
                result.put("score", totalScore);
                result.put("matchedKeywords", matched);
                scoredArticles.add(result);
            }
        }

        // 결과 반환
        if (scoredArticles.isEmpty()) {
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
            map.put("score", t.getScore() != null ? t.getScore().intValue() : 0);
            result.add(map);
        }
        return result;
    }


    // News Entity -> Map 변환 (JSON 직렬화용)
    private Map<String, Object> convertEntityToMap(News news) {
        Map<String, Object> map = new HashMap<>();
        map.put("title", news.getTitle());
        map.put("url", news.getUrl());
        map.put("press", news.getPress());
        map.put("time", news.getPublishedAt() != null ? news.getPublishedAt().toString() : null);

        try {
            String dbKeywords = news.getKeywords();

            if (dbKeywords != null && !dbKeywords.isBlank() && !dbKeywords.equals("[]")) {
                // JSON 문자열 파싱
                List<String> kwList = objectMapper.readValue(
                        dbKeywords,
                        new TypeReference<List<String>>() {}
                );
                map.put("keywords", kwList);
            } else {
                // 키워드가 없으면 빈 리스트
                map.put("keywords", new ArrayList<>());
            }
        } catch (Exception e) {
            // 파싱 실패 시 로그 남기고 빈 리스트 처리
            log.warn("[JSON PARSE ERROR] id={}, data={}", news.getId(), news.getKeywords());
            map.put("keywords", new ArrayList<>());
        }

        return map;
    }

    private Map<String, Object> parseJsonToMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("JSON parse failed: {}", json, e);
            return null;
        }
    }

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
}