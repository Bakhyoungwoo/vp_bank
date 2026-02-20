package com.example.vap_back.service.impl;

import com.example.vap_back.Entity.News;
import com.example.vap_back.repository.NewsRepository;
import com.example.vap_back.service.NewsCacheService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsCacheServiceImpl implements NewsCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final NewsRepository newsRepository;

    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

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

    @Override
    public List<Map<String, Object>> getLatestArticles(String category, int limit) {
        String key = "trend:" + category.toLowerCase() + ":articles";

        List<String> rawArticles = redisTemplate.opsForList().range(key, 0, limit - 1);

        if (rawArticles != null && !rawArticles.isEmpty()) {
            log.debug("[CACHE HIT] Redis에서 {} 뉴스 조회", category);
            return rawArticles.stream()
                    .map(this::parseJsonToMap)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        log.info("[CACHE MISS] DB에서 {} 뉴스 조회 및 캐싱 시도", category);

        List<News> dbNewsList = newsRepository.findTop50ByCategoryOrderByPublishedAtDesc(category.toLowerCase());

        if (dbNewsList.isEmpty()) {
            return new ArrayList<>();
        }

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
            redisTemplate.delete(key);
            redisTemplate.opsForList().rightPushAll(key, jsonList);
            redisTemplate.expire(key, CACHE_TTL);
        }

        return resultList.stream().limit(limit).collect(Collectors.toList());
    }

    @Override
    public void crawlAndSave(String category, List<Map<String, Object>> articles) {
        String key = "trend:" + category.toLowerCase() + ":articles";

        redisTemplate.delete(key);

        if (articles == null || articles.isEmpty()) return;

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
            redisTemplate.expire(key, CACHE_TTL);
        }
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
                List<String> kwList = objectMapper.readValue(dbKeywords, new TypeReference<List<String>>() {});
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

    private Map<String, Object> parseJsonToMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("JSON parse failed: {}", json, e);
            return null;
        }
    }
}
