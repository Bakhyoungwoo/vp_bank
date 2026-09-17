package com.example.vap_back.service.impl;

import com.example.vap_back.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    @Override
    public SseEmitter subscribe(String userId) {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);

        emitters.put(userId, emitter);
        log.info("[SSE Connected] UserId: {}", userId);

        emitter.onCompletion(() -> {
            log.info("[SSE Completed] UserId: {}", userId);
            emitters.remove(userId);
        });

        emitter.onTimeout(() -> {
            log.info("[SSE Timeout] UserId: {}", userId);
            emitters.remove(userId);
        });

        emitter.onError(e -> {
            log.warn("[SSE Error] UserId: {}, error: {}", userId, e.getMessage());
            emitters.remove(userId);
        });

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

    @Override
    public void broadcast(String title, String url) {
        if (emitters.isEmpty()) return;

        Map<String, String> eventData = new HashMap<>();
        eventData.put("title", title);
        eventData.put("url", url);

        emitters.forEach((userId, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("news")
                        .data(eventData));
            } catch (IOException e) {
                emitters.remove(userId);
            }
        });
        log.info("📢 [SSE Broadcast] Sent to {} clients: {}", emitters.size(), title);
    }
}
