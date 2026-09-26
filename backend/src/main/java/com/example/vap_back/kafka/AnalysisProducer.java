package com.example.vap_back.kafka;

import com.example.vap_back.dto.AnalysisRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnalysisProducer {
    public static final String TOPIC = "analysis-requested";

    private final KafkaTemplate<String, AnalysisRequestedEvent> analysisKafkaTemplate;

    public void publish(AnalysisRequestedEvent event) {
        analysisKafkaTemplate.send(TOPIC, event.getJobId(), event);
    }
}
