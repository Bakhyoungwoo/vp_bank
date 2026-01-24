package com.example.vap_back.kafka;

import com.example.vap_back.dto.UserEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UserConsumer {

    @KafkaListener(
            topics = "users-topic",
            groupId = "users-group",
            containerFactory = "userKafkaListenerContainerFactory"
    )
    public void consume(UserEvent event) {
        log.info("======================================");
        log.info("📥 [Kafka] User Event Received");
        log.info("User ID : {}", event.getUserId());
        log.info("Email   : {}", event.getEmail());
        log.info("Action  : {}", event.getAction());
        log.info("======================================");
    }
}
