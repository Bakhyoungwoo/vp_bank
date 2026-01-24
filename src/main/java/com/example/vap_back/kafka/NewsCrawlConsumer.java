package com.example.vap_back.kafka;

import com.example.vap_back.dto.NewsCrawlEvent;
import com.example.vap_back.service.NewsRedisService;
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

    private final NewsRedisService newsRedisService;

    @KafkaListener(topics = "crawl-news", groupId = "news-crawler-group")
    public void consume(NewsCrawlEvent event) {

        String category = event.getCategory();

        // 🔒 중복 크롤링 방지
        if (newsRedisService.isCrawling(category)) {
            return;
        }

        try {
            newsRedisService.markCrawling(category);

            // 🔥 여기서 실제 크롤링 수행
            List<Map<String, Object>> articles =
                    /* 기존 크롤링 로직 호출 */ List.of();

            newsRedisService.crawlAndSave(category, articles);

            log.info("뉴스 갱신 완료 - category={}", category);

        } catch (Exception e) {
            log.error("뉴스 갱신 실패 - category={}", category, e);
        } finally {
            newsRedisService.clearCrawling(category);
        }
    }
}
