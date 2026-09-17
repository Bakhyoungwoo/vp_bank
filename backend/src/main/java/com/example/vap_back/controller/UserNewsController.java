package com.example.vap_back.controller;

import com.example.vap_back.Entity.News;
import com.example.vap_back.Entity.User;
import com.example.vap_back.service.NewsRecommendationService;
import com.example.vap_back.service.NewsTrendingService;
import com.example.vap_back.service.NewsService;
import com.example.vap_back.service.NewsUserActivityService;
import com.example.vap_back.service.UserInterestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "News", description = "뉴스 조회/추천/클릭 로그")
public class UserNewsController {

    private final UserInterestService userInterestService;
    private final NewsTrendingService newsTrendingService;
    private final NewsUserActivityService newsUserActivityService;
    private final NewsRecommendationService newsRecommendationService;
    private final NewsService newsService;

    // 키워드 조회 (Redis)
    @Operation(summary = "카테고리 키워드 조회", description = "카테고리별 상위 키워드 목록을 Redis에서 조회합니다.")
    @GetMapping("/keywords/{category}")
    public ResponseEntity<?> getKeywords(@PathVariable String category) {
        String normalized = category.trim().toLowerCase();
        log.info("[KEYWORD API] raw='{}', normalized='{}'", category, normalized);
        return ResponseEntity.ok(
                newsTrendingService.getTopKeywords(normalized)
        );
    }

    // 뉴스 클릭 로그 (Redis)
    @Operation(summary = "뉴스 클릭 로그", description = "사용자의 뉴스 클릭 키워드를 Redis에 기록합니다.")
    @PostMapping("/click")
    public ResponseEntity<?> recordClick(
            @RequestBody Map<String, Object> body,
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String email = authentication.getName();
        User user = userInterestService.getUserByEmail(email);
        Long userId = user.getId();

        @SuppressWarnings("unchecked")
        List<String> keywords = (List<String>) body.get("keywords");

        if (keywords != null && !keywords.isEmpty()) {
            newsUserActivityService.addClickLog(userId, keywords);
        }

        return ResponseEntity.ok().build();
    }

    // 개인화 추천 뉴스 (Redis)
    @Operation(summary = "개인화 뉴스 추천", description = "사용자의 클릭 히스토리를 기반으로 개인화된 뉴스 목록을 추천합니다.")
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
                newsRecommendationService.recommendArticles(userId, 5);

        log.debug("recommend processed. userId={}, size={}", userId, result.size());

        return ResponseEntity.ok(result);
    }

    // 카테고리 뉴스 조회 (DB)
    @Operation(summary = "카테고리 뉴스 조회", description = "지정한 카테고리의 최신 뉴스 50건을 조회합니다.")
    @GetMapping("/{category}")
    public ResponseEntity<?> getNews(@PathVariable String category) {
        return ResponseEntity.ok(
                newsService.getNewsByCategory(category)
        );
    }

    @Operation(summary = "뉴스 검색", description = "제목 또는 내용에 키워드가 포함된 뉴스를 검색합니다.")
    @GetMapping("/search")
    public List<News> searchNews(@RequestParam("q") String q) {
        return newsService.searchNews(q);
    }
}
