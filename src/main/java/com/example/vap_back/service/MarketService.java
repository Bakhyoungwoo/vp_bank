package com.example.vap_back.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketService {
    private static final String YAHOO_CHART_URL =
            "https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?range=1d&interval=1m";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final List<MarketTarget> TARGETS = List.of(
            new MarketTarget("KOSPI", "코스피", "^KS11", "지수"),
            new MarketTarget("KOSDAQ", "코스닥", "^KQ11", "지수"),
            new MarketTarget("KOSPI200", "코스피200", "^KS200", "지수"),
            new MarketTarget("NIKKEI225", "닛케이225", "^N225", "지수"),
            new MarketTarget("USD/KRW", "원/달러", "KRW=X", "환율")
    );

    public Map<String, Object> getOverview() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (MarketTarget target : TARGETS) {
            try {
                items.add(fetch(target));
            } catch (Exception e) {
                log.warn("시세 조회 실패: {}", target.symbol(), e);
                Map<String, Object> unavailable = new LinkedHashMap<>();
                unavailable.put("code", target.code());
                unavailable.put("name", target.name());
                unavailable.put("unit", target.unit());
                unavailable.put("available", false);
                items.add(unavailable);
            }
        }
        return Map.of("items", items, "updatedAt", OffsetDateTime.now().toString());
    }

    private Map<String, Object> fetch(MarketTarget target) throws Exception {
        String body = restTemplate.getForObject(YAHOO_CHART_URL, String.class, target.symbol());
        JsonNode result = objectMapper.readTree(body).path("chart").path("result").path(0);
        JsonNode meta = result.path("meta");

        double price = meta.path("regularMarketPrice").asDouble(Double.NaN);
        double previous = meta.path("previousClose").asDouble(Double.NaN);
        if (Double.isNaN(price)) {
            JsonNode closes = result.path("indicators").path("quote").path(0).path("close");
            price = closes.isArray() && closes.size() > 0 ? closes.get(closes.size() - 1).asDouble(Double.NaN) : Double.NaN;
        }
        if (Double.isNaN(price)) throw new IllegalStateException("가격 데이터가 없습니다.");
        if (Double.isNaN(previous) || previous == 0) previous = price;

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", target.code());
        item.put("name", target.name());
        item.put("unit", target.unit());
        item.put("price", price);
        item.put("change", price - previous);
        item.put("changePercent", (price - previous) / previous * 100);
        item.put("marketTime", meta.path("regularMarketTime").asLong(0));
        item.put("available", true);
        return item;
    }

    private record MarketTarget(String code, String name, String symbol, String unit) {}
}
