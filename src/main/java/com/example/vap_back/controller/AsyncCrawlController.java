package com.example.vap_back.controller;

import com.example.vap_back.kafka.NewsCrawlProducer;
import com.example.vap_back.dto.CrawlJob;
import com.example.vap_back.service.CrawlJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/news")
public class AsyncCrawlController {

    private final NewsCrawlProducer newsCrawlProducer;
    private final CrawlJobService crawlJobService;

    /** Kafka publish ack까지 기다린 뒤 반환하는 비동기 작업 접수 API. */
    @PostMapping("/crawl/async")
    public CompletableFuture<ResponseEntity<Map<String, String>>> requestAsyncCrawl(
            @RequestParam String category) {
        String normalizedCategory = category.trim().toLowerCase();
        String jobId = UUID.randomUUID().toString();
        crawlJobService.create(jobId, normalizedCategory);
        return newsCrawlProducer.requestCrawlAndAwaitAck(jobId, normalizedCategory)
                .whenComplete((result, error) -> {
                    if (error != null) crawlJobService.markFailed(jobId, error.getMessage());
                })
                .thenApply(result -> ResponseEntity.accepted().body(Map.of(
                        "status", "accepted",
                        "jobId", jobId,
                        "category", normalizedCategory,
                        "topic", result.getRecordMetadata().topic(),
                        "offset", String.valueOf(result.getRecordMetadata().offset())
                )));
    }

    @GetMapping("/crawl/jobs/{jobId}")
    public ResponseEntity<CrawlJob> getCrawlJob(@PathVariable String jobId) {
        CrawlJob job = crawlJobService.get(jobId);
        return job == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(job);
    }
}
