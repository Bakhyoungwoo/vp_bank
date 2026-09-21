package com.example.vap_back.kafka;

import com.example.vap_back.dto.NewsCrawlEvent;
import com.example.vap_back.service.CrawlLockService;
import com.example.vap_back.service.NewsCacheService;
import com.example.vap_back.service.CrawlJobService;
import com.example.vap_back.config.TraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NewsCrawlConsumer {

    private final NewsCacheService newsCacheService;
    private final CrawlLockService crawlLockService;
    private final RestTemplate restTemplate;
    private final CrawlJobService crawlJobService;

    @Value("${crawler.base-url:http://localhost:8000}")
    private String crawlerBaseUrl;

    @KafkaListener(
            topics = "crawl-news",
            groupId = "news-crawler-group",
            containerFactory = "newsCrawlKafkaListenerContainerFactory"
    )
    public void consume(NewsCrawlEvent event) {
        String requestId = event.getRequestId() == null || event.getRequestId().isBlank()
                ? java.util.UUID.randomUUID().toString() : event.getRequestId();
        TraceContext.set(requestId);
        long totalStart = System.nanoTime();
        String category = event.getCategory();
        String jobId = event.getJobId();

        if (crawlLockService.isLocked(category)) {
            if (jobId != null) crawlJobService.markFailed(jobId, "A crawl is already running for this category");
            log.warn("[CRAWL] skipped because category is locked requestId={} jobId={} category={}", requestId, jobId, category);
            TraceContext.clear();
            return;
        }

        try {
            log.info("[CRAWL] start requestId={} jobId={} category={} kafkaWaitMs={}", requestId, jobId,
                    category, Math.max(0, System.currentTimeMillis() - event.getTimestamp()));
            if (jobId != null) crawlJobService.markProcessing(jobId);
            crawlLockService.lock(category);

            String crawlUrl = crawlerBaseUrl + "/crawl?category=" + category;
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Request-Id", requestId);
            long pythonStart = System.nanoTime();
            ResponseEntity<String> pythonResponse = restTemplate.exchange(
                    crawlUrl, HttpMethod.POST, new HttpEntity<>(null, headers), String.class);
            log.info("[CRAWL] python http complete requestId={} status={} elapsedMs={}", requestId,
                    pythonResponse.getStatusCode().value(), (System.nanoTime() - pythonStart) / 1_000_000);

            // Python crawler가 기사를 Spring 내부 API로 저장한 뒤 cache-aside key를 무효화한다.
            List<Map<String, Object>> articles = List.of();

            long saveStart = System.nanoTime();
            newsCacheService.crawlAndSave(category, articles);
            log.info("[CRAWL] result save complete requestId={} articleCount={} elapsedMs={}", requestId,
                    articles.size(), (System.nanoTime() - saveStart) / 1_000_000);
            if (jobId != null) crawlJobService.markCompleted(jobId);
            log.info("뉴스 갱신 완료 - category={}", category);

        } catch (Exception e) {
            if (jobId != null) crawlJobService.markFailed(jobId, e.getMessage());
            log.error("뉴스 갱신 실패 - category={}, DLQ 재시도 예정", category, e);
            throw e;  // DefaultErrorHandler가 3회 재시도 후 crawl-news-dlq로 전송
        } finally {
            log.info("[CRAWL] total complete requestId={} jobId={} elapsedMs={}", requestId, jobId,
                    (System.nanoTime() - totalStart) / 1_000_000);
            crawlLockService.unlock(category);
            TraceContext.clear();
        }
    }
}
