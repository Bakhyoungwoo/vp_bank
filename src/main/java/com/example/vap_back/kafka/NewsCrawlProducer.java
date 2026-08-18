package com.example.vap_back.kafka;

import com.example.vap_back.dto.NewsCrawlEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

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

    public CompletableFuture<SendResult<String, NewsCrawlEvent>> requestCrawlAndAwaitAck(String category) {
        NewsCrawlEvent event = new NewsCrawlEvent(category, System.currentTimeMillis());
        return kafkaTemplate.send(TOPIC, category, event);
    }
}
