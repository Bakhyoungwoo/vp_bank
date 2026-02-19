package com.example.vap_back.controller;

import com.example.vap_back.Entity.News;
import com.example.vap_back.Entity.User;
import com.example.vap_back.service.NewsRedisService;
import com.example.vap_back.service.NewsService;
import com.example.vap_back.service.UserInterestService;
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
public class UserNewsController {

    private final UserInterestService userInterestService;
    private final NewsRedisService newsRedisService;
    private final NewsService newsService;

    // 키워드 조회 (Redis)
    @GetMapping("/keywords/{category}")
    public ResponseEntity<?> getKeywords(@PathVariable String category) {
        String normalized = category.trim().toLowerCase();
        log.info("[KEYWORD API] raw='{}', normalized='{}'", category, normalized);
        return ResponseEntity.ok(
                newsRedisService.getTopKeywords(normalized)
        );
    }


    // 뉴스 클릭 로그 (Redis)
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
            newsRedisService.addClickLog(userId, keywords);
        }

        return ResponseEntity.ok().build();
    }

    // 개인화 추천 뉴스 (Redis)
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

        log.debug("recommend processed. userId={}, size={}", userId, result.size());

        return ResponseEntity.ok(result);
    }

    // 카테고리 뉴스 조회 (DB)
    @GetMapping("/{category}")
    public ResponseEntity<?> getNews(@PathVariable String category) {
        return ResponseEntity.ok(
                newsService.getNewsByCategory(category)
        );
    }

    @GetMapping("/search")
    public List<News> searchNews(@RequestParam("q") String q) {
        return newsService.searchNews(q);
    }
}
