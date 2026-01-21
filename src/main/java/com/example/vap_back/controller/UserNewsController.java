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

@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
public class UserNewsController {

    private final UserInterestService userInterestService;
    private final NewsRedisService newsRedisService;

    @GetMapping("/myfeed")
    public ResponseEntity<?> getMyCustomFeed(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }

        String email = authentication.getName();
        User user = userInterestService.getUserByEmail(email);

        List<String> userCategories = userInterestService.getUserCategoryList(user);

        Map<String, Object> finalFeed = new HashMap<>();

        for (String category : userCategories) {
            String cleanCategory = category.trim().toLowerCase();

            Map<String, Object> categoryData = new HashMap<>();
            categoryData.put("keywords",
                    newsRedisService.getTrendKeywords(cleanCategory));
            categoryData.put("articles",
                    newsRedisService.getLatestArticles(cleanCategory, 5));

            finalFeed.put(cleanCategory, categoryData);
        }
        System.out.println("User Categories = " + userCategories);
        System.out.println("=== [DEBUG] Feed Request End ===");

        // 2. 관심사별 Redis 뉴스 5개
        for (String category : userCategories) {
            Map<String, Object> categoryData = new HashMap<>();
            categoryData.put("keywords", newsRedisService.getTrendKeywords(category));
            categoryData.put(
                    "articles",
                    newsRedisService.getLatestArticles(category, 5)
            );

            finalFeed.put(category, categoryData);
        }

        return ResponseEntity.ok(finalFeed);
    }
}
