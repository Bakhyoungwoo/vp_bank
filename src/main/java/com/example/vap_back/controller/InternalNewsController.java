package com.example.vap_back.controller;

import com.example.vap_back.dto.NewsCreateRequest;
import com.example.vap_back.service.NewsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Internal", description = "내부 연동 API")
public class InternalNewsController {

    private final NewsService newsService;
    private final RestTemplate restTemplate;

    // Python -> Spring으로 뉴스 데이터 받기
    @PostMapping
    @Operation(summary = "뉴스 수신", description = "외부 Python 서버에서 뉴스 데이터를 받아 저장합니다.")
    public ResponseEntity<Void> receiveNews(@RequestBody NewsCreateRequest request) {
        log.info("[News Received] 외부에서 뉴스 수신: {}", request.getTitle());
        newsService.saveNews(request);
        return ResponseEntity.ok().build();
    }

    // Spring -> Python 크롤러 호출
    @PostMapping("/crawl")
    @Operation(summary = "크롤링 시작", description = "지정한 카테고리에 대해 Python 크롤러 서버에 크롤링 시작을 요청합니다.")
    public ResponseEntity<String> triggerCrawl(@RequestParam("category") String category) {
        log.info("[Command] 크롤링 시작 명령: category={}", category);

        try {
            String pythonUrl = "http://localhost:8000/crawl?category=" + category;
            restTemplate.postForEntity(pythonUrl, null, String.class);
            Thread.sleep(2000);
            return ResponseEntity.ok("크롤링 시작 요청 성공");
        } catch (Exception e) {
            log.error("크롤러 서버 연결 실패", e);
            return ResponseEntity.internalServerError().body("크롤러(Python) 서버가 꺼져 있는지 확인해주세요.");
        }
    }
}
