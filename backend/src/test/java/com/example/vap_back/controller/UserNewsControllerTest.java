package com.example.vap_back.controller;

import com.example.vap_back.Entity.News;
import com.example.vap_back.Entity.User;
import com.example.vap_back.config.JwtTokenProvider;
import com.example.vap_back.config.SecurityConfig;
import com.example.vap_back.service.*;
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

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserNewsController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class UserNewsControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean UserInterestService userInterestService;
    @MockBean NewsTrendingService newsTrendingService;
    @MockBean NewsUserActivityService newsUserActivityService;
    @MockBean NewsRecommendationService newsRecommendationService;
    @MockBean NewsService newsService;
    @MockBean JwtTokenProvider jwtTokenProvider;

    // ── getKeywords ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("키워드 조회 - 대문자 카테고리를 소문자로 정규화하여 서비스 호출")
    void getKeywords_normalizesCategory() throws Exception {
        given(newsTrendingService.getTopKeywords("it")).willReturn(
                List.of(Map.of("keyword", "java"), Map.of("keyword", "spring"), Map.of("keyword", "ai")));

        mockMvc.perform(get("/api/news/keywords/IT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].keyword").value("java"))
                .andExpect(jsonPath("$[1].keyword").value("spring"));

        then(newsTrendingService).should().getTopKeywords("it");
    }

    @Test
    @DisplayName("키워드 조회 - 빈 결과 반환")
    void getKeywords_empty() throws Exception {
        given(newsTrendingService.getTopKeywords("economy")).willReturn(List.of());

        mockMvc.perform(get("/api/news/keywords/economy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── recordClick ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("클릭 로그 기록 성공 - 인증된 사용자, 200")
    @WithMockUser(username = "test@test.com")
    void recordClick_authenticated_success() throws Exception {
        User user = User.builder().id(1L).email("test@test.com").password("enc").build();
        given(userInterestService.getUserByEmail("test@test.com")).willReturn(user);

        mockMvc.perform(post("/api/news/click")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("keywords", List.of("java", "spring")))))
                .andExpect(status().isOk());

        then(newsUserActivityService).should().addClickLog(eq(1L), anyList());
    }

    @Test
    @DisplayName("클릭 로그 기록 - keywords 비어 있으면 addClickLog 미호출")
    @WithMockUser(username = "test@test.com")
    void recordClick_emptyKeywords_skipsLog() throws Exception {
        User user = User.builder().id(1L).email("test@test.com").password("enc").build();
        given(userInterestService.getUserByEmail("test@test.com")).willReturn(user);

        mockMvc.perform(post("/api/news/click")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("keywords", List.of()))))
                .andExpect(status().isOk());

        then(newsUserActivityService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("클릭 로그 기록 실패 - 미인증 시 401")
    void recordClick_unauthenticated_401() throws Exception {
        mockMvc.perform(post("/api/news/click")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keywords\":[\"java\"]}"))
                .andExpect(status().isUnauthorized());
    }

    // ── recommendNews ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("개인화 추천 - 인증된 사용자, userId로 추천 호출")
    @WithMockUser(username = "test@test.com")
    void recommendNews_authenticated() throws Exception {
        User user = User.builder().id(2L).email("test@test.com").password("enc").build();
        given(userInterestService.getUserByEmail("test@test.com")).willReturn(user);
        given(newsRecommendationService.recommendArticles(2L, 5))
                .willReturn(List.of(Map.of("title", "추천 기사")));

        mockMvc.perform(get("/api/news/recommend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("추천 기사"));

        then(newsRecommendationService).should().recommendArticles(2L, 5);
    }

    @Test
    @DisplayName("개인화 추천 - 미인증 시 401 (SecurityConfig에서 authenticated() 요구)")
    void recommendNews_unauthenticated_401() throws Exception {
        mockMvc.perform(get("/api/news/recommend"))
                .andExpect(status().isUnauthorized());
    }

    // ── getNewsByCategory ─────────────────────────────────────────────────────

    @Test
    @DisplayName("카테고리 뉴스 조회 - 200, 뉴스 목록 반환")
    void getNewsByCategory_success() throws Exception {
        News news = News.builder()
                .id(1L).category("it").title("테스트 뉴스").url("http://example.com").build();
        given(newsService.getNewsByCategory("it")).willReturn(List.of(news));

        mockMvc.perform(get("/api/news/it"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].title").value("테스트 뉴스"));
    }

    // ── searchNews ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("뉴스 검색 - 키워드 포함된 뉴스 반환")
    void searchNews_success() throws Exception {
        News news = News.builder()
                .id(1L).category("it").title("Java 최신 동향").url("http://example.com").build();
        given(newsService.searchNews("java")).willReturn(List.of(news));

        mockMvc.perform(get("/api/news/search").param("q", "java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Java 최신 동향"));
    }

    @Test
    @DisplayName("뉴스 검색 - q 파라미터 누락 시 400")
    void searchNews_missingParam_400() throws Exception {
        mockMvc.perform(get("/api/news/search"))
                .andExpect(status().isBadRequest());
    }
}
