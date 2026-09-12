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
        // kafkaTemplate.send()는 브로커 메타데이터를 못 가져오면 max.block.ms(기본 60s) 동안
        // 동기적으로 블로킹된 뒤 예외를 던진다. 이 이벤트 발행은 로그인/회원가입의 부가 기능이므로
        // Kafka 장애가 로그인 자체를 막지 않도록 여기서 예외를 흡수한다.
        try {
            kafkaTemplate.send(TOPIC, event);
            log.info("[Kafka] UserEvent sent: {}", event.getAction());
        } catch (Exception e) {
            log.warn("[Kafka] UserEvent 발행 실패 (로그인/회원가입은 계속 진행): action={}, reason={}",
                    event.getAction(), e.getMessage());
        }
    }
}
