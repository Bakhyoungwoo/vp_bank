package com.example.vap_back.controller;

import com.example.vap_back.service.MarketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
