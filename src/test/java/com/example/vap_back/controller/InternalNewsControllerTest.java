package com.example.vap_back.controller;

import com.example.vap_back.config.JwtTokenProvider;
import com.example.vap_back.config.SecurityConfig;
import com.example.vap_back.dto.NewsCreateRequest;
import com.example.vap_back.service.NewsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InternalNewsController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class InternalNewsControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean NewsService newsService;
    @MockBean RestTemplate restTemplate;
    @MockBean JwtTokenProvider jwtTokenProvider;

    // ── receiveNews ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("뉴스 수신 성공 - 크롤러로부터 데이터 수신 후 200")
    void receiveNews_success() throws Exception {
        Map<String, Object> body = Map.of(
                "category", "it",
                "title", "테스트 뉴스",
                "url", "http://example.com/news/1",
                "press", "테스트 언론",
                "publishedAt", "2024-01-01T12:00:00"
        );
        willDoNothing().given(newsService).saveNews(any(NewsCreateRequest.class));

        mockMvc.perform(post("/api/internal/news")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        then(newsService).should().saveNews(any(NewsCreateRequest.class));
    }

    // ── triggerCrawl ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("크롤링 트리거 성공 - Python 서버 응답 후 200")
    void triggerCrawl_success() throws Exception {
        // RestTemplate mock이 즉시 반환하더라도 컨트롤러 내 Thread.sleep(2000)이 실행됨
        given(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .willReturn(ResponseEntity.ok("ok"));

        mockMvc.perform(post("/api/internal/news/crawl").param("category", "it"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("크롤링 시작 요청 성공")));
    }

    @Test
    @DisplayName("크롤링 트리거 실패 - Python 서버 연결 불가 시 500")
    void triggerCrawl_crawlerUnavailable_500() throws Exception {
        given(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .willThrow(new RuntimeException("Connection refused"));

        mockMvc.perform(post("/api/internal/news/crawl").param("category", "it"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(containsString("크롤러(Python) 서버가 꺼져 있는지 확인해주세요.")));
    }

    @Test
    @DisplayName("크롤링 트리거 실패 - category 파라미터 누락 시 400")
    void triggerCrawl_missingParam_400() throws Exception {
        mockMvc.perform(post("/api/internal/news/crawl"))
                .andExpect(status().isBadRequest());
    }
}
