package com.example.vap_back.controller;

import com.example.vap_back.dto.NewsCreateRequest;
import com.example.vap_back.service.NewsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/news")
@CrossOrigin // 내부 테스트용
public class InternalNewsController {

    private final NewsService newsService;
    // Fast API -> Spring
    @PostMapping
    public ResponseEntity<Void> receiveNews(
            @RequestBody NewsCreateRequest request
    ) {
        newsService.saveNews(request);
        return ResponseEntity.ok().build();
    }
}
