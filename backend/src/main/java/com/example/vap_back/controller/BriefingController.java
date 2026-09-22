package com.example.vap_back.controller;

import com.example.vap_back.Entity.BriefingHistory;
import com.example.vap_back.service.BriefingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/briefing")
@RequiredArgsConstructor
@Tag(name = "Briefing", description = "개인화 관심종목 브리핑 생성/이력 조회")
public class BriefingController {

    private final BriefingService briefingService;

    @Operation(summary = "개인화 브리핑 생성", description = "인증된 사용자의 관심종목에 대해 마지막 브리핑 이후 변화를 요약한다.")
    @PostMapping
    public ResponseEntity<Map<String, Object>> generateBriefing(Authentication authentication) {
        return briefingService.generateBriefing(authentication.getName());
    }

    @Operation(summary = "브리핑 이력 조회", description = "인증된 사용자의 브리핑 생성 이력을 최신순으로 반환한다.")
    @GetMapping("/history")
    public List<BriefingHistory> getMyBriefingHistory(Authentication authentication) {
        return briefingService.getMyBriefingHistory(authentication.getName());
    }
}
