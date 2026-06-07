package com.example.vap_back.kafka;

import com.example.vap_back.dto.NewsCrawlEvent;
import com.example.vap_back.service.CrawlLockService;
import com.example.vap_back.service.NewsCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NewsCrawlConsumer {

    private final NewsCacheService newsCacheService;
    private final CrawlLockService crawlLockService;

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

            List<Map<String, Object>> articles =
                    /* 기존 크롤링 로직 호출 */ List.of();

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
