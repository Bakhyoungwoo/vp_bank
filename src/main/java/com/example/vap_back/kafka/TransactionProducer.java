package com.example.vap_back.kafka;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransactionProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public void sendTransaction(String jsonMessage) {
        // AI 서버가 구독할 토픽 이름: transaction-topic
        kafkaTemplate.send("transaction-topic", jsonMessage);
    }
}