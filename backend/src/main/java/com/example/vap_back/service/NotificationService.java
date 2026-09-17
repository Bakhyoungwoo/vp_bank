package com.example.vap_back.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface NotificationService {
    SseEmitter subscribe(String userId);
    void broadcast(String title, String url);
}
