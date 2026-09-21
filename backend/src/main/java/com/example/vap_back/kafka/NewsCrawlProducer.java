package com.example.vap_back.kafka;

import com.example.vap_back.dto.NewsCrawlEvent;
import com.example.vap_back.config.TraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class NewsCrawlProducer {

    private final KafkaTemplate<String, NewsCrawlEvent> kafkaTemplate;
    private static final String TOPIC = "crawl-news";

    public CompletableFuture<SendResult<String, NewsCrawlEvent>> requestCrawlAndAwaitAck(String jobId, String category) {
        String requestId = TraceContext.currentOrNew();
        NewsCrawlEvent event = new NewsCrawlEvent(jobId, category, System.currentTimeMillis(), requestId);
        long start = System.nanoTime();
        log.info("[CRAWL_QUEUE] publish start requestId={} jobId={} category={}", requestId, jobId, category);
        return kafkaTemplate.send(TOPIC, category, event).whenComplete((result, error) ->
                log.info("[CRAWL_QUEUE] publish complete requestId={} jobId={} elapsedMs={} success={}",
                        requestId, jobId, (System.nanoTime() - start) / 1_000_000, error == null));
    }
}
