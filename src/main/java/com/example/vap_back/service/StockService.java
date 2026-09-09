package com.example.vap_back.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;
import com.example.vap_back.Entity.News;
import com.example.vap_back.service.NewsService;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {
    private final RestTemplate restTemplate;
    private final NewsService newsService;

    @Value("${ai.base-url:http://localhost:8000}")
    private String aiBaseUrl;

    public Map<String, Object> search(String query, int limit) {
        String url = aiBaseUrl + "/stocks/search?query="
                + UriUtils.encodeQueryParam(query, StandardCharsets.UTF_8)
                + "&limit=" + limit;
        try {
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            return response != null ? response : Map.of("query", query, "items", List.of());
        } catch (Exception e) {
            log.warn("AI/OpenBB stock search unavailable: query={}, reason={}", query, e.getMessage());
            return Map.of("query", query, "items", List.of(), "provider", "openbb/yfinance");
        }
    }

    public Map<String, Object> detail(String symbol, int days) {
        String url = aiBaseUrl + "/stocks/"
                + UriUtils.encodePathSegment(symbol, StandardCharsets.UTF_8)
                + "?days=" + days;
        try {
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            return response != null ? response : Map.of("symbol", symbol, "history", List.of());
        } catch (Exception e) {
            log.warn("AI/OpenBB stock detail unavailable: symbol={}, reason={}", symbol, e.getMessage());
            return Map.of("symbol", symbol, "provider", "openbb/yfinance", "history", List.of());
        }
    }

    public Map<String, Object> financials(String symbol, int limit) {
        String url = aiBaseUrl + "/stocks/"
                + UriUtils.encodePathSegment(symbol, StandardCharsets.UTF_8)
                + "/financials?limit=" + limit;
        try {
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            return response != null ? response : Map.of("symbol", symbol, "items", List.of(), "available", false);
        } catch (Exception e) {
            log.warn("AI/OpenBB stock financials unavailable: symbol={}, reason={}", symbol, e.getMessage());
            return Map.of("symbol", symbol, "provider", "openbb/yfinance", "items", List.of(), "available", false);
        }
    }

    public Map<String, Object> news(String symbol, int limit) {
        try {
            List<Map<String, Object>> items = newsService.searchNews(symbol).stream()
                    .limit(limit)
                    .map(this::toNewsItem)
                    .toList();
            return Map.of(
                    "symbol", symbol,
                    "provider", "naver-crawler",
                    "available", !items.isEmpty(),
                    "items", items
            );
        } catch (Exception e) {
            log.warn("Crawler news unavailable: symbol={}, reason={}", symbol, e.getMessage());
            return Map.of("symbol", symbol, "provider", "naver-crawler", "items", List.of(), "available", false);
        }
    }

    private Map<String, Object> toNewsItem(News news) {
        return Map.of(
                "id", news.getId(),
                "title", news.getTitle(),
                "content", news.getContent() == null ? "" : news.getContent(),
                "url", news.getUrl(),
                "press", news.getPress() == null ? "" : news.getPress(),
                "publishedAt", news.getPublishedAt() == null ? "" : news.getPublishedAt().toString(),
                "keywords", news.getKeywords() == null ? "[]" : news.getKeywords()
        );
    }
}
