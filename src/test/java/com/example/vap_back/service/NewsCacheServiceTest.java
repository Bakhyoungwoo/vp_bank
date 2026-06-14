package com.example.vap_back.service;

import com.example.vap_back.Entity.News;
import com.example.vap_back.repository.NewsRepository;
import com.example.vap_back.service.impl.NewsCacheServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NewsCacheServiceTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ObjectMapper objectMapper;
    @Mock NewsRepository newsRepository;
    @Mock ListOperations<String, String> listOps;

    @InjectMocks
    NewsCacheServiceImpl newsCacheService;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForList()).willReturn(listOps);
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("캐시 HIT - Redis에 데이터 있으면 DB 조회 없이 캐시 반환")
    void getLatestArticles_cacheHit_returnsFromCache() throws Exception {
        // given
        List<String> cachedJsons = List.of("{\"title\":\"캐시 기사\",\"url\":\"http://a.com\"}");
        Map<String, Object> parsedMap = Map.of("title", "캐시 기사", "url", "http://a.com");

        given(listOps.range("trend:it:articles", 0, 9)).willReturn(cachedJsons);
        given(objectMapper.readValue(anyString(), any(TypeReference.class))).willReturn(parsedMap);

        // when
        List<Map<String, Object>> result = newsCacheService.getLatestArticles("it", 10);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("title")).isEqualTo("캐시 기사");
        then(newsRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("캐시 MISS + DB 데이터 있음 - DB 조회 후 Redis 캐싱")
    void getLatestArticles_cacheMiss_loadsFromDbAndCaches() throws Exception {
        // given
        given(listOps.range("trend:it:articles", 0, 9)).willReturn(List.of());

        News news = News.builder().id(1L).title("DB 기사").url("http://db.com")
                .press("언론사").keywords(null).build();
        given(newsRepository.findTop50ByCategoryOrderByPublishedAtDesc("it")).willReturn(List.of(news));
        given(objectMapper.writeValueAsString(any())).willReturn("{\"title\":\"DB 기사\"}");

        // when
        List<Map<String, Object>> result = newsCacheService.getLatestArticles("it", 10);

        // then
        assertThat(result).hasSize(1);
        then(redisTemplate).should().delete("trend:it:articles");
        then(listOps).should().rightPushAll(eq("trend:it:articles"), anyList());
        then(redisTemplate).should().expire(eq("trend:it:articles"), any(Duration.class));
    }

    @Test
    @DisplayName("캐시 MISS + DB 비어있음 - 빈 리스트 반환")
    void getLatestArticles_cacheMiss_dbEmpty_returnsEmptyList() {
        // given
        given(listOps.range("trend:it:articles", 0, 9)).willReturn(List.of());
        given(newsRepository.findTop50ByCategoryOrderByPublishedAtDesc("it")).willReturn(List.of());

        // when
        List<Map<String, Object>> result = newsCacheService.getLatestArticles("it", 10);

        // then
        assertThat(result).isEmpty();
        then(listOps).should(never()).rightPushAll(anyString(), anyList());
    }

    // ──────────────────────────────────────────────────────
    // Redis 장애 fallback (CircuitBreaker) 테스트
    // ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Redis 장애 fallback - DB에서 직접 조회하여 반환")
    void getLatestArticlesFallback_returnsFromDb() {
        // given
        News news = News.builder().id(1L).title("Fallback 기사").url("http://fallback.com")
                .press("언론사").keywords(null).build();
        given(newsRepository.findTop50ByCategoryOrderByPublishedAtDesc("it"))
                .willReturn(List.of(news));

        // when: fallback 메서드를 직접 호출 (AOP 없이 로직만 검증)
        List<Map<String, Object>> result = newsCacheService.getLatestArticlesFallback(
                "it", 10, new RuntimeException("Redis connection refused"));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("title")).isEqualTo("Fallback 기사");
        assertThat(result.get(0).get("url")).isEqualTo("http://fallback.com");
    }

    @Test
    @DisplayName("Redis 장애 fallback - limit 이하로만 반환")
    void getLatestArticlesFallback_respectsLimit() {
        // given: DB에 3건이지만 limit=2
        List<News> dbNews = List.of(
                News.builder().id(1L).title("기사1").url("http://1.com").keywords(null).build(),
                News.builder().id(2L).title("기사2").url("http://2.com").keywords(null).build(),
                News.builder().id(3L).title("기사3").url("http://3.com").keywords(null).build()
        );
        given(newsRepository.findTop50ByCategoryOrderByPublishedAtDesc("economy"))
                .willReturn(dbNews);

        // when
        List<Map<String, Object>> result = newsCacheService.getLatestArticlesFallback(
                "economy", 2, new RuntimeException("Redis timeout"));

        // then
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("Redis 장애 fallback - DB도 비어있으면 빈 리스트 반환")
    void getLatestArticlesFallback_dbEmpty_returnsEmptyList() {
        // given
        given(newsRepository.findTop50ByCategoryOrderByPublishedAtDesc("politics"))
                .willReturn(List.of());

        // when
        List<Map<String, Object>> result = newsCacheService.getLatestArticlesFallback(
                "politics", 10, new RuntimeException("Redis down"));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Redis 장애 fallback - keywords null인 뉴스는 빈 키워드 리스트로 매핑")
    void getLatestArticlesFallback_nullKeywords_mappedAsEmptyList() {
        // given
        News news = News.builder().id(1L).title("키워드 없는 기사").url("http://no-kw.com")
                .keywords(null).build();
        given(newsRepository.findTop50ByCategoryOrderByPublishedAtDesc("world"))
                .willReturn(List.of(news));

        // when
        List<Map<String, Object>> result = newsCacheService.getLatestArticlesFallback(
                "world", 10, new RuntimeException("Redis error"));

        // then
        assertThat(result.get(0).get("keywords")).isEqualTo(List.of());
    }

    // ──────────────────────────────────────────────────────

    @Test
    @DisplayName("크롤링 저장 - 기존 키 삭제 후 새 데이터 저장 및 만료 설정")
    void crawlAndSave_deletesOldAndSavesNew() throws Exception {
        // given
        List<Map<String, Object>> articles = List.of(
                Map.of("title", "기사1", "url", "http://url1.com"),
                Map.of("title", "기사2", "url", "http://url2.com"));
        given(objectMapper.writeValueAsString(any())).willReturn("{\"title\":\"기사\"}");

        // when
        newsCacheService.crawlAndSave("economy", articles);

        // then
        then(redisTemplate).should().delete("trend:economy:articles");
        then(listOps).should().rightPushAll(eq("trend:economy:articles"), anyList());
        then(redisTemplate).should().expire(eq("trend:economy:articles"), any(Duration.class));
    }
}
