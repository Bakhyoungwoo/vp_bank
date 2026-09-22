package com.example.vap_back.controller;

import com.example.vap_back.Entity.BriefingHistory;
import com.example.vap_back.config.JwtTokenProvider;
import com.example.vap_back.config.SecurityConfig;
import com.example.vap_back.service.BriefingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BriefingController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class BriefingControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean BriefingService briefingService;
    @MockBean JwtTokenProvider jwtTokenProvider;

    // ── generateBriefing ─────────────────────────────────────────────────────

    @Test
    @DisplayName("브리핑 생성 - 인증된 사용자, 200")
    @WithMockUser(username = "test@test.com")
    void generateBriefing_success() throws Exception {
        given(briefingService.generateBriefing("test@test.com"))
                .willReturn(ResponseEntity.ok(Map.of("status", "ready", "llmNarrative", "요약")));

        mockMvc.perform(post("/api/ai/briefing").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ready"));
    }

    @Test
    @DisplayName("브리핑 생성 - 관심종목이 없으면 400 그대로 전달")
    @WithMockUser(username = "test@test.com")
    void generateBriefing_emptyWatchlist_returns400() throws Exception {
        given(briefingService.generateBriefing("test@test.com"))
                .willReturn(ResponseEntity.badRequest().body(Map.of("message", "관심종목이 없습니다")));

        mockMvc.perform(post("/api/ai/briefing").with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("브리핑 생성 실패 - 미인증 시 401")
    void generateBriefing_unauthenticated_401() throws Exception {
        mockMvc.perform(post("/api/ai/briefing").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    // ── getMyBriefingHistory ─────────────────────────────────────────────────

    @Test
    @DisplayName("브리핑 이력 조회 - 인증된 사용자, 목록 반환")
    @WithMockUser(username = "test@test.com")
    void getMyBriefingHistory_success() throws Exception {
        BriefingHistory history = BriefingHistory.builder()
                .id(1L).userId(1L).generatedAt(LocalDateTime.of(2024, 1, 1, 12, 0))
                .symbols("AAPL").status("ready").build();
        given(briefingService.getMyBriefingHistory("test@test.com")).willReturn(List.of(history));

        mockMvc.perform(get("/api/ai/briefing/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbols").value("AAPL"));
    }

    @Test
    @DisplayName("브리핑 이력 조회 실패 - 미인증 시 401")
    void getMyBriefingHistory_unauthenticated_401() throws Exception {
        mockMvc.perform(get("/api/ai/briefing/history"))
                .andExpect(status().isUnauthorized());
    }
}
