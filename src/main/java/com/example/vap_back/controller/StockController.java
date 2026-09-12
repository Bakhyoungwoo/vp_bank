package com.example.vap_back.controller;

import com.example.vap_back.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
@Tag(name = "Stocks", description = "OpenBB 기반 종목 검색 및 상세 조회")
public class StockController {
    private final StockService stockService;

    @GetMapping("/search")
    @Operation(summary = "종목 검색")
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit
    ) {
        if (query.isBlank() || limit < 1 || limit > 25) {
            return ResponseEntity.badRequest().body(Map.of("message", "query와 limit을 확인해주세요."));
        }
        return ResponseEntity.ok(stockService.search(query, limit));
    }

    @GetMapping("/{symbol}")
    @Operation(summary = "종목 상세 조회")
    public ResponseEntity<Map<String, Object>> detail(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "30") int days
    ) {
        if (symbol.isBlank() || days < 1 || days > 3650) {
            return ResponseEntity.badRequest().body(Map.of("message", "symbol과 days를 확인해주세요."));
        }
        return ResponseEntity.ok(stockService.detail(symbol, days));
    }

    @GetMapping("/{symbol}/financials")
    @Operation(summary = "醫낅ぉ ?щТ 議고쉶")
    public ResponseEntity<Map<String, Object>> financials(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "5") int limit
    ) {
        if (symbol.isBlank() || limit < 1 || limit > 20) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid symbol or limit"));
        }
        return ResponseEntity.ok(stockService.financials(symbol, limit));
    }

    @GetMapping("/{symbol}/news")
    @Operation(summary = "醫낅ぉ 愿???댁뒪 議고쉶")
    public ResponseEntity<Map<String, Object>> news(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "10") int limit
    ) {
        if (symbol.isBlank() || limit < 1 || limit > 30) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid symbol or limit"));
        }
        return ResponseEntity.ok(stockService.news(symbol, limit));
    }
}
