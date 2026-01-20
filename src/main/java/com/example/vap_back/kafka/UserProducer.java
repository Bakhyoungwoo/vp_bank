package com.example.vap_back.kafka;

import com.example.vap_back.dto.UserEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void send(UserEvent event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("employee-topic", message);
        } catch (Exception e) {
            throw new RuntimeException("Kafka 전송 실패", e);
        }
    }
}
