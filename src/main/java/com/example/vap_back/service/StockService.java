package com.example.vap_back.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {
    private final RestTemplate restTemplate;

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
}
