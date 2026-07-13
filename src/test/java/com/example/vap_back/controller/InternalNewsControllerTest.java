package com.example.vap_back.controller;

import com.example.vap_back.config.JwtTokenProvider;
import com.example.vap_back.config.SecurityConfig;
import com.example.vap_back.dto.NewsCreateRequest;
import com.example.vap_back.kafka.NewsCrawlProducer;
import com.example.vap_back.service.NewsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
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
    @MockBean NewsCrawlProducer newsCrawlProducer;
    @MockBean JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("?댁뒪 ?섏떊 ?깃났 - ?щ·?щ줈遺???곗씠???섏떊 ??200")
    void receiveNews_success() throws Exception {
        Map<String, Object> body = Map.of(
                "category", "it",
                "title", "?뚯뒪???댁뒪",
                "url", "http://example.com/news/1",
                "press", "?뚯뒪???몃줎",
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
    @DisplayName("?щ·留??몃━嫄??깃났 - Kafka ?묐떟 ??202")
    void triggerCrawl_success() throws Exception {
        willDoNothing().given(newsCrawlProducer).requestCrawl("it");

        mockMvc.perform(post("/api/internal/news/crawl").param("category", "it"))
                .andExpect(status().isAccepted())
                .andExpect(content().string("crawl request accepted"));

        then(newsCrawlProducer).should().requestCrawl("it");
    }

    @Test
    @DisplayName("?щ·留??몃━嫄??ㅽ뙣 - category ?뚮씪誘명꽣 ?꾨씫 ??400")
    void triggerCrawl_missingParam_400() throws Exception {
        mockMvc.perform(post("/api/internal/news/crawl"))
                .andExpect(status().isBadRequest());
    }
}
