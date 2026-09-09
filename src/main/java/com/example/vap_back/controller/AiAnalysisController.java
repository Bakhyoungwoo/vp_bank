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
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiAnalysisController {
    private final RestTemplate restTemplate;

    @Value("${ai.base-url:http://localhost:8000}")
    private String aiBaseUrl;

    @PostMapping("/stocks/{symbol}/analysis")
    public ResponseEntity<Map<String, Object>> analyzeStock(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "30") int days
    ) {
        if (symbol.isBlank() || days < 5 || days > 3650) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid symbol or days"));
        }
        String url = aiBaseUrl + "/ai/stocks/"
                + UriUtils.encodePathSegment(symbol, StandardCharsets.UTF_8)
                + "/analysis?days=" + days;
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
}
