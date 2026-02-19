package com.example.vap_back.controller;

import com.example.vap_back.dto.NewsCreateRequest;
import com.example.vap_back.service.NewsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/news")
@CrossOrigin
public class InternalNewsController {

    private final NewsService newsService;
    private final RestTemplate restTemplate;

    // Python -> Spring (크롤링한 뉴스 데이터 받기)
    @PostMapping
    public ResponseEntity<Void> receiveNews(@RequestBody NewsCreateRequest request) {
        log.info(" [News Received] 크롤러로부터 뉴스 도착: {}", request.getTitle());
        newsService.saveNews(request);
        return ResponseEntity.ok().build();
    }

    //  Spring -> Python, 재분석
    @PostMapping("/crawl")
    public ResponseEntity<String> triggerCrawl(@RequestParam("category") String category) {
        log.info(" [Command] 크롤링 시작 명령: category={}", category);

        try {
            // Python FastAPI 서버로 요청 전송 (localhost:8000)
            String pythonUrl = "http://localhost:8000/crawl?category=" + category;

            // 파이썬 서버에게 POST 요청을 보냄 (응답은 String으로 받음)
            restTemplate.postForEntity(pythonUrl, null, String.class);

            Thread.sleep(2000);

            return ResponseEntity.ok("크롤링 시작 요청 성공");
        } catch (Exception e) {
            log.error("크롤러 서버 연결 실패", e);
            return ResponseEntity.internalServerError().body("크롤러 서버(Python)가 켜져 있는지 확인해주세요.");
        }
    }
}