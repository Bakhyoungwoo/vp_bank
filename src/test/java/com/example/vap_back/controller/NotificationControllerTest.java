package com.example.vap_back.controller;

import com.example.vap_back.config.JwtTokenProvider;
import com.example.vap_back.config.SecurityConfig;
import com.example.vap_back.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class NotificationControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean NotificationService notificationService;
    @MockBean JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("SSE 구독 성공 - text/event-stream 컨텐츠 타입, 서비스 호출")
    void subscribe_success() throws Exception {
        given(notificationService.subscribe("user1")).willReturn(new SseEmitter(30_000L));

        mockMvc.perform(get("/api/notifications/subscribe")
                        .param("userId", "user1")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted());

        then(notificationService).should().subscribe("user1");
    }

    @Test
    @DisplayName("SSE 구독 실패 - userId 파라미터 누락 시 400")
    void subscribe_missingUserId_400() throws Exception {
        mockMvc.perform(get("/api/notifications/subscribe"))
                .andExpect(status().isBadRequest());
    }
}
