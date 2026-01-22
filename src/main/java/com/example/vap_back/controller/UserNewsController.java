package com.example.vap_back.controller;

import com.example.vap_back.Entity.User;
import com.example.vap_back.service.NewsRedisService;
import com.example.vap_back.service.UserInterestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class UserNewsController {

    private final UserInterestService userInterestService;
    private final NewsRedisService newsRedisService;


    // 프론트엔드로부터 기사 URL과 키워드 리스트를 전달받아 Redis에 기록.
    @PostMapping("/click")
    public ResponseEntity<?> recordClick(
            @RequestBody Map<String, Object> body,
            Authentication authentication
    )
    {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String email = authentication.getName();
        User user = userInterestService.getUserByEmail(email);
        Long userId = user.getId();

        List<String> keywords = (List<String>) body.get("keywords");
        if (keywords != null && !keywords.isEmpty()) {
            newsRedisService.addClickLog(userId, keywords);
        }

        System.out.println("=== /api/news/click HIT ===");
        System.out.println("auth = " + authentication);
        System.out.println("body = " + body);

        return ResponseEntity.ok().build();
    }

    @GetMapping("/recommend")
    public ResponseEntity<?> recommendNews(Authentication authentication) {

        Long userId = 0L;

        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            String email = authentication.getName();
            User user = userInterestService.getUserByEmail(email);
            userId = user.getId();
        }

        List<Map<String, Object>> result =
                newsRedisService.recommendArticles(userId, 5);

        System.out.println("=== [RECOMMEND RESULT] ===");
        System.out.println(result);

        return ResponseEntity.ok(result);
    }
}
