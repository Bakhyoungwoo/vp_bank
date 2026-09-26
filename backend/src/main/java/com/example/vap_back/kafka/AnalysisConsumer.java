package com.example.vap_back.kafka;

import com.example.vap_back.Entity.AnalysisJob;
import com.example.vap_back.Entity.BriefingHistory;
import com.example.vap_back.Entity.ProcessedEvent;
import com.example.vap_back.dto.AnalysisRequestedEvent;
import com.example.vap_back.repository.AnalysisJobRepository;
import com.example.vap_back.repository.BriefingHistoryRepository;
import com.example.vap_back.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "analysis.worker.enabled", havingValue = "true", matchIfMissing = true)
public class AnalysisConsumer {

    private final AnalysisJobRepository analysisJobRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final BriefingHistoryRepository briefingHistoryRepository;
    private final RestTemplate restTemplate;

    @Value("${ai.base-url:http://localhost:8000}")
    private String aiBaseUrl;

    @KafkaListener(topics = AnalysisProducer.TOPIC, containerFactory = "analysisKafkaListenerContainerFactory")
    @Transactional
    public void consume(AnalysisRequestedEvent event) {
        if (processedEventRepository.existsById(event.getEventId())) {
            log.info("[ANALYSIS] duplicate event skipped: {}", event.getEventId());
            return;
        }

        AnalysisJob job = analysisJobRepository.findByJobId(event.getJobId())
                .orElseThrow(() -> new IllegalStateException("Analysis job not found: " + event.getJobId()));

        if ("COMPLETED".equals(job.getStatus()) || briefingHistoryRepository.existsByJobId(event.getJobId())) {
            processedEventRepository.save(new ProcessedEvent(event.getEventId()));
            log.info("[ANALYSIS] completed job skipped: {}", event.getJobId());
            return;
        }

        job.markProcessing();

        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(aiBaseUrl + "/ai/briefing")
                    .queryParam("symbols", event.getSymbols())
                    .queryParam("days", event.getDays())
                    .build()
                    .encode(StandardCharsets.UTF_8)
                    .toUri();
            Map<String, Object> result = restTemplate.postForObject(uri, null, Map.class);
            String narrative = result == null ? null : (String) result.get("llmNarrative");
            job.markCompleted(narrative);
            briefingHistoryRepository.save(BriefingHistory.builder()
                    .userId(event.getUserId())
                    .jobId(event.getJobId())
                    .generatedAt(LocalDateTime.now())
                    .symbols(event.getSymbols())
                    .status(result == null ? "completed" : (String) result.getOrDefault("status", "completed"))
                    .llmNarrative(narrative)
                    .build());
            processedEventRepository.save(new ProcessedEvent(event.getEventId()));
        } catch (Exception exception) {
            job.markFailed(exception.getMessage());
            log.error("[ANALYSIS] failed jobId={} eventId={}", event.getJobId(), event.getEventId(), exception);
            throw new IllegalStateException("Analysis failed: " + event.getJobId(), exception);
        }
    }
}
