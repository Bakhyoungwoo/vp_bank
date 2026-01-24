package com.example.vap_back.kafka;

import com.example.vap_back.dto.UserEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProducer {

    private final KafkaTemplate<String, UserEvent> kafkaTemplate;

    private static final String TOPIC = "users-topic";

    public void send(UserEvent event) {
        kafkaTemplate.send(TOPIC, event);
        log.info("[Kafka] UserEvent sent: {}", event.getAction());
    }
}
