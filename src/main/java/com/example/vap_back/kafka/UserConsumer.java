package com.example.vap_back.kafka;

import com.example.vap_back.Entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UserConsumer {

    @KafkaListener(topics = "users-topic", groupId = "users-group")
    public void consume(User user) {
        log.info("======================================");
        log.info("📥 [Kafka] User Created Event Received");
        log.info("ID        : {}", user.getId());
        log.info("Name      : {}", user.getName());
        log.info("Department: {}", user.getDepartment());

        log.info("======================================");
    }
}