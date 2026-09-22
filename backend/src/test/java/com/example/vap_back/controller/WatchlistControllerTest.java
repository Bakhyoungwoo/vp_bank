package com.example.vap_back.controller;

import com.example.vap_back.Entity.Watchlist;
import com.example.vap_back.config.JwtTokenProvider;
import com.example.vap_back.config.SecurityConfig;
import com.example.vap_back.dto.WatchlistRequestDto;
import com.example.vap_back.service.WatchlistService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WatchlistController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class WatchlistControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean WatchlistService watchlistService;
    @MockBean JwtTokenProvider jwtTokenProvider;

    // ── addToWatchlist ───────────────────────────────────────────────────────

    @Test
    @DisplayName("관심종목 추가 - 인증된 사용자, 200")
    @WithMockUser(username = "test@test.com")
    void addToWatchlist_success() throws Exception {
        WatchlistRequestDto dto = new WatchlistRequestDto();
        dto.setSymbol("AAPL");

        given(watchlistService.addToWatchlist(eq("test@test.com"), any())).willReturn("관심종목에 추가됨");

        mockMvc.perform(post("/api/watchlist")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(content().string("관심종목에 추가됨"));

        then(watchlistService).should().addToWatchlist(eq("test@test.com"), any());
    }

    @Test
    @DisplayName("관심종목 추가 실패 - 미인증 시 401")
    void addToWatchlist_unauthenticated_401() throws Exception {
        WatchlistRequestDto dto = new WatchlistRequestDto();
        dto.setSymbol("AAPL");

        mockMvc.perform(post("/api/watchlist")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized());
    }

    // ── removeFromWatchlist ──────────────────────────────────────────────────

    @Test
    @DisplayName("관심종목 삭제 - 인증된 사용자, 200")
    @WithMockUser(username = "test@test.com")
    void removeFromWatchlist_success() throws Exception {
        given(watchlistService.removeFromWatchlist("test@test.com", "AAPL")).willReturn("관심종목에서 제거됨");

        mockMvc.perform(delete("/api/watchlist/AAPL").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string("관심종목에서 제거됨"));
    }

    // ── getMyWatchlist ───────────────────────────────────────────────────────

    @Test
    @DisplayName("관심종목 목록 조회 - 인증된 사용자, 목록 반환")
    @WithMockUser(username = "test@test.com")
    void getMyWatchlist_success() throws Exception {
        Watchlist watchlist = Watchlist.builder()
                .id(1L).userId(1L).symbol("AAPL")
                .addedAt(LocalDateTime.of(2024, 1, 1, 12, 0))
                .build();
        given(watchlistService.getMyWatchlist("test@test.com")).willReturn(List.of(watchlist));

        mockMvc.perform(get("/api/watchlist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].symbol").value("AAPL"));
    }

    @Test
    @DisplayName("관심종목 목록 조회 실패 - 미인증 시 401")
    void getMyWatchlist_unauthenticated_401() throws Exception {
        mockMvc.perform(get("/api/watchlist"))
                .andExpect(status().isUnauthorized());
    }
}
