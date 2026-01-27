package com.example.vap_back.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaConsumerService {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    // Kafka 리스너
    // topics: Python에서 보낸 토픽 이름과 일치해야 함
    // groupId: 여러 서버가 떴을 때 중복 수신 방지용 ID
    @KafkaListener(topics = "news-alert", groupId = "vap-group")
    public void consumeNewsAlert(String message) {
        log.info("📨 [Kafka Received] {}", message);

        try {
            // JSON 문자열을 자바 객체로 파싱
            JsonNode node = objectMapper.readTree(message);
            String title = node.get("title").asText();

            // url
            String url = node.has("url") ? node.get("url").asText() : "#";

            // 알림 서비스 호출
            notificationService.broadcast(title, url);

        } catch (Exception e) {
            log.error(" [Kafka Consume Error] 메시지 파싱 실패", e);
        }
    }
}