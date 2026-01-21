package com.example.vap_back.controller;

import com.example.vap_back.Entity.User;
import com.example.vap_back.service.NewsRedisService;
import com.example.vap_back.service.UserInterestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<?> getMyCustomFeed(@SessionAttribute("user") User user) {
        // 1. MySQL에서 유저의 관심 카테고리 목록 조회
        List<String> userCategories = userInterestService.getUserCategoryList(user);

        Map<String, Object> finalFeed = new HashMap<>();

        // 2. 각 카테고리별로 Redis에서 데이터 수집
        for (String category : userCategories) {
            Map<String, Object> categoryData = new HashMap<>();
            categoryData.put("keywords", newsRedisService.getTrendKeywords(category));
            categoryData.put("articles", newsRedisService.getLatestArticles(category));

            finalFeed.put(category, categoryData);
        }

        return ResponseEntity.ok(finalFeed);
    }
}