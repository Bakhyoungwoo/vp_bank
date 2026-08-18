package com.example.vap_back.kafka;

import com.example.vap_back.dto.NewsCrawlEvent;
import com.example.vap_back.service.CrawlLockService;
import com.example.vap_back.service.NewsCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;
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

    @Value("${crawler.base-url:http://localhost:8000}")
    private String crawlerBaseUrl;

    @KafkaListener(
            topics = "crawl-news",
            groupId = "news-crawler-group",
            containerFactory = "newsCrawlKafkaListenerContainerFactory"
    )
    public void consume(NewsCrawlEvent event) {
        String category = event.getCategory();

        if (crawlLockService.isLocked(category)) {
            return;
        }

        try {
            crawlLockService.lock(category);

            String crawlUrl = crawlerBaseUrl + "/crawl?category=" + category;
            restTemplate.postForEntity(crawlUrl, null, String.class);

            // Python crawler가 기사를 Spring 내부 API로 저장한 뒤 cache-aside key를 무효화한다.
            List<Map<String, Object>> articles = List.of();

            newsCacheService.crawlAndSave(category, articles);
            log.info("뉴스 갱신 완료 - category={}", category);

        } catch (Exception e) {
            log.error("뉴스 갱신 실패 - category={}, DLQ 재시도 예정", category, e);
            throw e;  // DefaultErrorHandler가 3회 재시도 후 crawl-news-dlq로 전송
        } finally {
            crawlLockService.unlock(category);
        }
    }
}
