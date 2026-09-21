package com.example.vap_back.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiAnalysisController {
    private final RestTemplate restTemplate;

    @Value("${ai.base-url:http://localhost:8000}")
    private String aiBaseUrl;

    /** Proxies an analysis request to the Python service and preserves its status payload. */
    @PostMapping("/stocks/{symbol}/analysis")
    public ResponseEntity<Map<String, Object>> analyzeStock(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "30") int days
    ) {
        if (symbol.isBlank() || days < 5 || days > 3650) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid symbol or days"));
        }
        URI url = UriComponentsBuilder.fromHttpUrl(aiBaseUrl + "/ai/stocks/{symbol}/analysis") // Python analysis endpoint.
                .queryParam("days", days)
                .build()
                .expand(symbol)
                .encode(StandardCharsets.UTF_8)
                .toUri();
        try {
            Map<String, Object> result = restTemplate.postForObject(url, null, Map.class);
            return ResponseEntity.ok(result != null ? result : Map.of(
                    "symbol", symbol, "status", "unavailable"));
        } catch (Exception exception) {
            return ResponseEntity.status(502).body(Map.of(
                    "symbol", symbol,
                    "status", "unavailable",
                    "message", "AI analysis service unavailable"));
        }
    }

    /** Proxies a stock comparison request (2~5 symbols) to the Python service. */
    @PostMapping("/stocks/compare")
    public ResponseEntity<Map<String, Object>> compareStocks(
            @RequestParam String symbols,
            @RequestParam(defaultValue = "90") int days
    ) {
        if (symbols.isBlank() || days < 5 || days > 3650) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid symbols or days"));
        }
        URI url = UriComponentsBuilder.fromHttpUrl(aiBaseUrl + "/ai/stocks/compare")
                .queryParam("symbols", symbols)
                .queryParam("days", days)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();
        try {
            return ResponseEntity.ok(restTemplate.postForObject(url, null, Map.class));
        } catch (org.springframework.web.client.HttpClientErrorException.BadRequest exception) {
            return ResponseEntity.badRequest().body(Map.of("message", "종목은 2~5개를 입력해야 합니다."));
        } catch (Exception exception) {
            return ResponseEntity.status(502).body(Map.of(
                    "symbols", symbols,
                    "status", "unavailable",
                    "message", "AI comparison service unavailable"));
        }
    }

    /** Proxies a stock news-impact analysis request to the Python service. */
    @PostMapping("/stocks/{symbol}/news-impact")
    public ResponseEntity<Map<String, Object>> analyzeNewsImpact(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "30") int days
    ) {
        if (symbol.isBlank() || limit < 1 || limit > 30 || days < 5 || days > 3650) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid symbol, limit, or days"));
        }
        URI url = UriComponentsBuilder.fromHttpUrl(aiBaseUrl + "/ai/stocks/{symbol}/news-impact")
                .queryParam("limit", limit)
                .queryParam("days", days)
                .build()
                .expand(symbol)
                .encode(StandardCharsets.UTF_8)
                .toUri();
        try {
            Map<String, Object> result = restTemplate.postForObject(url, null, Map.class);
            return ResponseEntity.ok(result != null ? result : Map.of(
                    "symbol", symbol, "status", "unavailable"));
        } catch (Exception exception) {
            return ResponseEntity.status(502).body(Map.of(
                    "symbol", symbol,
                    "status", "unavailable",
                    "message", "AI news-impact service unavailable"));
        }
    }

    /** Proxies a price-move (surge/plunge cause) analysis request to the Python service. */
    @PostMapping("/stocks/{symbol}/price-move")
    public ResponseEntity<Map<String, Object>> analyzePriceMove(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "10") int limit
    ) {
        if (symbol.isBlank() || days < 5 || days > 3650 || limit < 1 || limit > 30) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid symbol, days, or limit"));
        }
        URI url = UriComponentsBuilder.fromHttpUrl(aiBaseUrl + "/ai/stocks/{symbol}/price-move")
                .queryParam("days", days)
                .queryParam("limit", limit)
                .build()
                .expand(symbol)
                .encode(StandardCharsets.UTF_8)
                .toUri();
        try {
            Map<String, Object> result = restTemplate.postForObject(url, null, Map.class);
            return ResponseEntity.ok(result != null ? result : Map.of(
                    "symbol", symbol, "status", "unavailable"));
        } catch (Exception exception) {
            return ResponseEntity.status(502).body(Map.of(
                    "symbol", symbol,
                    "status", "unavailable",
                    "message", "AI price-move service unavailable"));
        }
    }
}
