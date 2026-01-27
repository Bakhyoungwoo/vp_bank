package com.example.vap_back.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    // 모든 사용자의 연결을 관리하는 저장소 (Thread-Safe)
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    // 클라이언트 연결 (Subscribe)
    public SseEmitter subscribe(String userId) {
        // 연결 유지 시간 설정 (기본 1시간: 3600000ms)
        SseEmitter emitter = new SseEmitter(60 * 60 * 1000L);

        emitters.put(userId, emitter);
        log.info("👤 [SSE Connected] UserId: {}", userId);

        // 연결 종료 혹은 타임아웃 시 목록에서 제거
        emitter.onCompletion(() -> {
            log.info("👋 [SSE Completed] UserId: {}", userId);
            emitters.remove(userId);
        });

        emitter.onTimeout(() -> {
            log.info("⏰ [SSE Timeout] UserId: {}", userId);
            emitters.remove(userId);
        });

        // 에러 방지용 더미 데이터 전송
        try {
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data("Connected successfully!"));
        } catch (IOException e) {
            log.error(" [SSE Error] Initial connection failed", e);
            emitters.remove(userId);
        }

        return emitter;
    }

    // Kafka Consumer가 이 메서드를 호출
    public void broadcast(String title, String url) {
        if (emitters.isEmpty()) return;

        // {"title": "...", "url": "..."} 형태로 전송
        Map<String, String> eventData = new HashMap<>();
        eventData.put("title", title);
        eventData.put("url", url);

        emitters.forEach((userId, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("news")
                        .data(eventData)); // Map을 넣으면 자동으로 JSON 변환됨
            } catch (IOException e) {
                emitters.remove(userId);
            }
        });
        log.info("📢 [SSE Broadcast] Sent to {} clients: {}", emitters.size(), title);
    }
}