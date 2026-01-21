package com.example.vap_back.kafka;

import com.example.vap_back.Entity.Transaction;
import com.example.vap_back.repository.TransactionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class TransactionConsumer {

    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;

    // AI 서버가 결과값을 보내주는 토픽을 리슨합니다.
    @KafkaListener(topics = "result-topic", groupId = "user-group")
    @Transactional
    public void consumeResult(String message) {
        try {
            // 전달받은 JSON 문자열 파싱
            JsonNode node = objectMapper.readTree(message);
            Long id = node.get("id").asLong();
            boolean isFraud = node.get("isFraud").asBoolean();

            // DB에서 해당 트랜잭션을 찾아 상태 업데이트
            transactionRepository.findById(id).ifPresent(transaction -> {
                transaction.updateFraudStatus(isFraud);
                if (isFraud) {
                    System.out.println("🚨 이상 거래 탐지됨! ID: " + id);
                }
            });

        } catch (Exception e) {
            System.err.println("데이터 파싱 중 오류 발생: " + e.getMessage());
        }
    }
}