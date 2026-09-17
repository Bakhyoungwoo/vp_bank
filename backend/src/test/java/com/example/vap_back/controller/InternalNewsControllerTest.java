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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @Test
    @DisplayName("뉴스 수신 성공 - 서버로부터 데이터 수신 시 200")
    void receiveNews_success() throws Exception {
        Map<String, Object> body = Map.of(
                "category", "it",
                "title", "테스트 뉴스",
                "url", "http://example.com/news/1",
                "press", "테스트 프레스",
                "publishedAt", "2024-01-01T12:00:00"
        );
        willDoNothing().given(newsService).saveNews(any(NewsCreateRequest.class));

        mockMvc.perform(post("/api/internal/news")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        then(newsService).should().saveNews(any(NewsCreateRequest.class));
    }

    @Test
    @DisplayName("크롤링 트리거 성공 - Python 크롤링 완료 후 200")
    void triggerCrawl_success() throws Exception {
        given(restTemplate.postForEntity(eq("http://localhost:8000/crawl?category=it"), any(), eq(String.class)))
                .willReturn(ResponseEntity.ok("ok"));

        mockMvc.perform(post("/api/internal/news/crawl").param("category", "it"))
                .andExpect(status().isOk())
                .andExpect(content().string("crawl completed"));

        then(restTemplate).should().postForEntity(eq("http://localhost:8000/crawl?category=it"), any(), eq(String.class));
    }

    @Test
    @DisplayName("크롤링 트리거 실패 - category 파라미터 누락 시 400")
    void triggerCrawl_missingParam_400() throws Exception {
        mockMvc.perform(post("/api/internal/news/crawl"))
                .andExpect(status().isBadRequest());
    }
}
