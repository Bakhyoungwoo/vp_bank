package com.example.vap_back.controller;

import com.example.vap_back.dto.NewsCreateRequest;
import com.example.vap_back.service.NewsService;
import com.example.vap_back.config.TraceContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/news")
@CrossOrigin
@Tag(name = "Internal", description = "내부 연동 API")
public class InternalNewsController {

    private final NewsService newsService;
    private final RestTemplate restTemplate;

    @Value("${crawler.base-url:http://localhost:8000}")
    private String crawlerBaseUrl;

    // Python -> Spring으로 뉴스 데이터 받기
    @PostMapping
    @Operation(summary = "뉴스 수신", description = "내부 Python 서버에서 뉴스 데이터를 받아 저장합니다.")
    public ResponseEntity<Void> receiveNews(@RequestBody NewsCreateRequest request) {
        log.info("[News Received] 내부에서 뉴스 수신: {}", request.getTitle());
        long saveStart = System.nanoTime();
        newsService.saveNews(request);
        log.info("[CRAWL] article db save complete requestId={} elapsedMs={}",
                TraceContext.currentOrNew(), (System.nanoTime() - saveStart) / 1_000_000);
        return ResponseEntity.ok().build();
    }

    // Spring -> Python 동기 크롤링 호출 (완료까지 대기)
    @PostMapping("/crawl")
    @Operation(summary = "크롤링 완료까지 대기", description = "지정한 카테고리로 Python 크롤러를 호출하고 완료 응답까지 기다립니다.")
    public ResponseEntity<String> triggerCrawl(@RequestParam("category") String category) {
        String normalizedCategory = category.trim().toLowerCase();
        log.info("[Command] 동기 크롤링 시작: category={}", normalizedCategory);

        String crawlUrl = crawlerBaseUrl + "/crawl?category=" + normalizedCategory;
        String requestId = TraceContext.currentOrNew();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Request-Id", requestId);
        long pythonStart = System.nanoTime();
        ResponseEntity<String> response = restTemplate.exchange(
                crawlUrl, HttpMethod.POST, new HttpEntity<>(null, headers), String.class);
        log.info("[CRAWL] synchronous python complete requestId={} category={} status={} elapsedMs={}",
                requestId, normalizedCategory, response.getStatusCode().value(),
                (System.nanoTime() - pythonStart) / 1_000_000);

        return ResponseEntity.ok("crawl completed");
    }
}
