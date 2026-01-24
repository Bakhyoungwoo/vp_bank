package com.example.vap_back.kafka;

import com.example.vap_back.dto.NewsCrawlEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NewsCrawlProducer {

    private final KafkaTemplate<String, NewsCrawlEvent> kafkaTemplate;
    private static final String TOPIC = "crawl-news";

    public void requestCrawl(String category) {
        NewsCrawlEvent event =
                new NewsCrawlEvent(category, System.currentTimeMillis());

        kafkaTemplate.send(TOPIC, category, event);
    }
}
