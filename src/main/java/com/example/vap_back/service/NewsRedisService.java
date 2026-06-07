package com.example.vap_back.service;

import com.example.vap_back.Entity.News;
import com.example.vap_back.repository.NewsRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsRedisService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final NewsRepository newsRepository;

    private static final List<String> CATEGORIES =
            List.of("economy", "society", "it", "politics", "world", "culture");

    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    @PostConstruct
    public void checkConnection() {
        log.info("======= [REDIS CONNECTION CHECK] =======");
        try {
            String ping = Objects.requireNonNull(redisTemplate.getConnectionFactory())
                    .getConnection().ping();
            log.info("Redis Ping: {}", ping);
            warmUpCache();
        } catch (Exception e) {
            log.error("Redis connection check failed", e);
        }
        log.info("========================================");
    }

    // Cold Start 방지: 서버 기동 시 카테고리별 최신 50건을 Redis에 선적재
    public void warmUpCache() {
        log.info("======= [CACHE WARM-UP START] =======");
        for (String category : CATEGORIES) {
            String key = "trend:" + category + ":articles";
            if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                log.info("[WARM-UP] 스킵 - 키 존재: {}", key);
                continue;
            }
            try {
                List<News> articles = newsRepository.findTop50ByCategoryOrderByPublishedAtDesc(category);
                List<String> jsonList = new ArrayList<>();
                for (News news : articles) {
                    jsonList.add(objectMapper.writeValueAsString(convertEntityToMap(news)));
                }
                if (!jsonList.isEmpty()) {
                    redisTemplate.opsForList().rightPushAll(key, jsonList);
                    redisTemplate.expire(key, CACHE_TTL);
                    log.info("[WARM-UP] 완료 - category={}, {}건 적재", category, jsonList.size());
                }
            } catch (Exception e) {
                log.error("[WARM-UP] 실패 - category={}", category, e);
            }
        }
        log.info("======= [CACHE WARM-UP END] =======");
    }

    private Map<String, Object> convertEntityToMap(News news) {
        Map<String, Object> map = new HashMap<>();
        map.put("title", news.getTitle());
        map.put("url", news.getUrl());
        map.put("press", news.getPress());
        map.put("time", news.getPublishedAt() != null ? news.getPublishedAt().toString() : null);

        try {
            String dbKeywords = news.getKeywords();
            if (dbKeywords != null && !dbKeywords.isBlank() && !dbKeywords.equals("[]")) {
                List<String> kwList = objectMapper.readValue(
                        dbKeywords,
                        new TypeReference<List<String>>() {}
                );
                map.put("keywords", kwList);
            } else {
                map.put("keywords", new ArrayList<>());
            }
        } catch (Exception e) {
            log.warn("[JSON PARSE ERROR] id={}, data={}", news.getId(), news.getKeywords());
            map.put("keywords", new ArrayList<>());
        }

        return map;
    }
}
