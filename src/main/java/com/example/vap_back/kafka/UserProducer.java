package com.example.vap_back.kafka;

import com.example.vap_back.dto.UserEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // log 사용을 위해 필요
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase; // 필수 Import
import org.springframework.transaction.event.TransactionalEventListener; // 필수 Import

@Slf4j // log 변수 자동 생성
@Component
@RequiredArgsConstructor
public class UserProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    //  스프링이 이벤트를 감지해서 실행.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserEvent(UserEvent event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("users-topic", message);
            log.info("[Kafka] 메시지 발행 성공: {}", event.getAction());
        } catch (Exception e) {
            log.error("[Kafka] 전송 실패: {}", e.getMessage());
        }
    }
}