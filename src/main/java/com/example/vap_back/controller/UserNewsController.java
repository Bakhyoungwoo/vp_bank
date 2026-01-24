package com.example.vap_back.controller;

import com.example.vap_back.Entity.User;
import com.example.vap_back.service.NewsRedisService;
import com.example.vap_back.service.UserInterestService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class UserNewsController {

    private final UserInterestService userInterestService;
    private final NewsRedisService newsRedisService;
    private final MeterRegistry meterRegistry;

    // 🔹 뉴스 클릭 (Before 측정용)
    @PostMapping("/click")
    public ResponseEntity<?> recordClick(
            @RequestBody Map<String, Object> body,
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // ⏱ 타이머 시작
        Timer.Sample sample = Timer.start(meterRegistry);

        String email = authentication.getName();
        User user = userInterestService.getUserByEmail(email);
        Long userId = user.getId();

        List<String> keywords = (List<String>) body.get("keywords");
        if (keywords != null && !keywords.isEmpty()) {
            // 🔴 BEFORE: Redis 동기 처리
            newsRedisService.addClickLog(userId, keywords);
        }

        // ✅ 성능에 영향 적은 DEBUG 로그
        log.debug("news click processed. userId={}, keywordCount={}",
                userId,
                keywords != null ? keywords.size() : 0
        );

        // ⏱ 타이머 종료 + 기록
        sample.stop(
                Timer.builder("api.news.click")
                        .tag("version", "before")
                        .register(meterRegistry)
        );

        return ResponseEntity.ok().build();
    }

    // 🔹 뉴스 추천
    @GetMapping("/recommend")
    public ResponseEntity<?> recommendNews(Authentication authentication) {

        Long userId = 0L;

        if (authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {

            String email = authentication.getName();
            User user = userInterestService.getUserByEmail(email);
            userId = user.getId();
        }

        List<Map<String, Object>> result =
                newsRedisService.recommendArticles(userId, 5);

        // ❌ 결과 전체 출력 금지
        log.debug("recommend request processed. userId={}, resultSize={}",
                userId, result.size());

        return ResponseEntity.ok(result);
    }
}
