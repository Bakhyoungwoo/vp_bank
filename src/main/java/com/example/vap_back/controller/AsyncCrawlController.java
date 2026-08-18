package com.example.vap_back.controller;

import com.example.vap_back.kafka.NewsCrawlProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/news")
public class AsyncCrawlController {

    private final NewsCrawlProducer newsCrawlProducer;

    /** Kafka publish ack까지 기다린 뒤 반환하는 비동기 작업 접수 API. */
    @PostMapping("/crawl/async")
    public CompletableFuture<ResponseEntity<Map<String, String>>> requestAsyncCrawl(
            @RequestParam String category) {
        return newsCrawlProducer.requestCrawlAndAwaitAck(category)
                .thenApply(result -> ResponseEntity.accepted().body(Map.of(
                        "status", "accepted",
                        "category", category,
                        "topic", result.getRecordMetadata().topic(),
                        "offset", String.valueOf(result.getRecordMetadata().offset())
                )));
    }
}
