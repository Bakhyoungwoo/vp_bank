package com.example.vap_back.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketService {
    private static final List<MarketTarget> TARGETS = List.of(
            new MarketTarget("KOSPI", "코스피", "지수"),
            new MarketTarget("KOSDAQ", "코스닥", "지수"),
            new MarketTarget("KOSPI200", "코스피200", "지수"),
            new MarketTarget("NASDAQ", "나스닥", "지수"),
            new MarketTarget("SP500", "S&P500", "지수"),
            new MarketTarget("USD/KRW", "원/달러", "환율")
    );

    private final RestTemplate restTemplate;

    @Value("${ai.base-url:http://localhost:8000}")
    private String aiBaseUrl;

    /** Backend는 금융 Provider를 직접 호출하지 않고 AI 서비스의 OpenBB adapter 결과만 전달한다. */
    public Map<String, Object> getOverview() {
        try {
            Map<String, Object> response = restTemplate.getForObject(
                    aiBaseUrl + "/market/overview", Map.class);
            return response != null ? response : unavailableResponse();
        } catch (Exception e) {
            log.warn("AI/OpenBB market service unavailable: {}", e.getMessage());
            return unavailableResponse();
        }
    }

    public Map<String, Object> getHistory(String symbol, int days) {
        String encodedSymbol = UriUtils.encodePathSegment(symbol, StandardCharsets.UTF_8);
        String url = aiBaseUrl + "/market/history/" + encodedSymbol + "?days=" + days;
        try {
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            return response != null ? response : Map.of("symbol", symbol, "items", List.of());
        } catch (Exception e) {
            log.warn("AI/OpenBB market history unavailable: symbol={}, reason={}", symbol, e.getMessage());
            return Map.of("symbol", symbol, "provider", "openbb/yfinance", "items", List.of());
        }
    }

    private Map<String, Object> unavailableResponse() {
        List<Map<String, Object>> items = TARGETS.stream().map(target -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", target.code());
            item.put("name", target.name());
            item.put("unit", target.unit());
            item.put("available", false);
            return item;
        }).toList();
        return Map.of(
                "items", items,
                "provider", "openbb/yfinance",
                "updatedAt", OffsetDateTime.now().toString()
        );
    }

    private record MarketTarget(String code, String name, String unit) {}
}
