package com.example.vap_back.controller;

import com.example.vap_back.service.MarketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
@Tag(name = "Market", description = "국내외 주요 지수 조회")
public class MarketController {
    private final MarketService marketService;

    @Operation(summary = "주요 지수 조회", description = "코스피, 코스닥 등 주요 시장 지수를 조회합니다.")
    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> overview() {
        return ResponseEntity.ok(marketService.getOverview());
    }

    @Operation(summary = "종목·지수 과거 가격 조회", description = "OpenBB를 통해 차트용 OHLCV 데이터를 조회합니다.")
    @GetMapping("/history/{symbol}")
    public ResponseEntity<Map<String, Object>> history(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "30") int days
    ) {
        if (days < 1 || days > 3650) {
            return ResponseEntity.badRequest().body(Map.of("message", "days는 1에서 3650 사이여야 합니다."));
        }
        return ResponseEntity.ok(marketService.getHistory(symbol, days));
    }
}
