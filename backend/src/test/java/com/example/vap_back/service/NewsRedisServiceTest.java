package com.example.vap_back.service;

import com.example.vap_back.service.impl.NewsRecommendationServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class NewsRedisServiceTest {

    @Mock NewsCacheService newsCacheService;
    @Mock NewsUserActivityService newsUserActivityService;

    @InjectMocks
    NewsRecommendationServiceImpl newsRecommendationService;

    @Test
    @DisplayName("사용자 관심사 없음 - 'it' 카테고리 최신 뉴스 반환")
    void recommendArticles_noUserInterests_returnsItDefault() {
        // given
        given(newsUserActivityService.getTopInterestsWithScores(1L, 20)).willReturn(Map.of());

        List<Map<String, Object>> itArticles = List.of(Map.of("title", "IT 기사", "url", "http://it.com"));
        given(newsCacheService.getLatestArticles("it", 5)).willReturn(itArticles);

        // when
        List<Map<String, Object>> result = newsRecommendationService.recommendArticles(1L, 5);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("title")).isEqualTo("IT 기사");
    }

    @Test
    @DisplayName("관심사 매칭 있음 - 점수 내림차순 정렬 후 limit 적용")
    void recommendArticles_withMatchingKeywords_returnsSortedByScore() {
        // given
        Map<String, Double> userInterests = Map.of("AI", 10.0, "Spring", 5.0);
        given(newsUserActivityService.getTopInterestsWithScores(1L, 20)).willReturn(userInterests);

        // AI+Spring 두 키워드 모두 매칭 → score 15.0
        Map<String, Object> highScore = new HashMap<>(Map.of(
                "title", "AI Spring 기사", "url", "http://a.com",
                "keywords", List.of("AI", "Spring")));
        // AI 하나만 매칭 → score 10.0
        Map<String, Object> lowScore = new HashMap<>(Map.of(
                "title", "AI 기사", "url", "http://b.com",
                "keywords", List.of("AI")));

        given(newsCacheService.getLatestArticles("economy", 50)).willReturn(List.of(highScore));
        given(newsCacheService.getLatestArticles("society", 50)).willReturn(List.of(lowScore));
        given(newsCacheService.getLatestArticles("it", 50)).willReturn(List.of());
        given(newsCacheService.getLatestArticles("politics", 50)).willReturn(List.of());
        given(newsCacheService.getLatestArticles("world", 50)).willReturn(List.of());
        given(newsCacheService.getLatestArticles("culture", 50)).willReturn(List.of());

        // when
        List<Map<String, Object>> result = newsRecommendationService.recommendArticles(1L, 2);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).get("title")).isEqualTo("AI Spring 기사"); // score 15.0
        assertThat(result.get(1).get("title")).isEqualTo("AI 기사");         // score 10.0
    }

    @Test
    @DisplayName("관심사 있지만 매칭 없음 - 'it' 카테고리 최신 뉴스 반환")
    void recommendArticles_noMatches_returnsItDefault() {
        // given
        given(newsUserActivityService.getTopInterestsWithScores(1L, 20))
                .willReturn(Map.of("희귀키워드", 10.0));

        // 모든 카테고리 기사에 "희귀키워드" 없음
        Map<String, Object> article = new HashMap<>(Map.of(
                "title", "매칭없는 기사", "url", "http://c.com",
                "keywords", List.of("AI", "Spring")));
        given(newsCacheService.getLatestArticles(anyString(), eq(50))).willReturn(List.of(article));

        List<Map<String, Object>> itDefault = List.of(Map.of("title", "IT 기본", "url", "http://it.com"));
        given(newsCacheService.getLatestArticles("it", 5)).willReturn(itDefault);

        // when
        List<Map<String, Object>> result = newsRecommendationService.recommendArticles(1L, 5);

        // then
        assertThat(result.get(0).get("title")).isEqualTo("IT 기본");
    }
}
