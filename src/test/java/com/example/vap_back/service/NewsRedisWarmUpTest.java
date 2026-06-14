package com.example.vap_back.service;

import com.example.vap_back.Entity.News;
import com.example.vap_back.repository.NewsRepository;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NewsRedisWarmUpTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ObjectMapper objectMapper;
    @Mock NewsRepository newsRepository;
    @Mock ListOperations<String, String> listOps;

    @InjectMocks
    NewsRedisService newsRedisService;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForList()).willReturn(listOps);
    }

    @Test
    @DisplayName("워밍업 - 모든 키가 없으면 6개 카테고리 전부 DB 조회 후 Redis 적재")
    void warmUpCache_noKeysExist_loadsAllCategories() throws Exception {
        // given
        given(redisTemplate.hasKey(anyString())).willReturn(false);

        News news = News.builder().id(1L).title("기사").url("http://a.com")
                .press("언론사").keywords(null).build();
        given(newsRepository.findTop50ByCategoryOrderByPublishedAtDesc(anyString()))
                .willReturn(List.of(news));
        given(objectMapper.writeValueAsString(any())).willReturn("{\"title\":\"기사\"}");

        // when
        newsRedisService.warmUpCache();

        // then: CATEGORIES = [economy, society, it, politics, world, culture] 6개
        then(newsRepository).should(times(6)).findTop50ByCategoryOrderByPublishedAtDesc(anyString());
        then(listOps).should(times(6)).rightPushAll(anyString(), anyList());
        then(redisTemplate).should(times(6)).expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("워밍업 - 이미 키가 있는 카테고리는 DB 조회 없이 스킵")
    void warmUpCache_allKeysExist_skipsAll() {
        // given: 모든 카테고리 키가 이미 Redis에 존재
        given(redisTemplate.hasKey(anyString())).willReturn(true);

        // when
        newsRedisService.warmUpCache();

        // then
        then(newsRepository).shouldHaveNoInteractions();
        then(listOps).should(never()).rightPushAll(anyString(), anyList());
    }

    @Test
    @DisplayName("워밍업 - 일부 카테고리만 키 없음 → 해당 카테고리만 DB 조회")
    void warmUpCache_partialKeysExist_onlyLoadsMissing() throws Exception {
        // given: "economy"만 키 있고 나머지 5개는 없음
        given(redisTemplate.hasKey("trend:economy:articles")).willReturn(true);
        given(redisTemplate.hasKey(argThat(k -> !k.equals("trend:economy:articles"))))
                .willReturn(false);

        News news = News.builder().id(1L).title("기사").url("http://a.com")
                .keywords(null).build();
        given(newsRepository.findTop50ByCategoryOrderByPublishedAtDesc(anyString()))
                .willReturn(List.of(news));
        given(objectMapper.writeValueAsString(any())).willReturn("{\"title\":\"기사\"}");

        // when
        newsRedisService.warmUpCache();

        // then: economy 제외 5개만 적재
        then(newsRepository).should(times(5)).findTop50ByCategoryOrderByPublishedAtDesc(anyString());
        then(listOps).should(times(5)).rightPushAll(anyString(), anyList());
    }

    @Test
    @DisplayName("워밍업 - DB 결과가 비어있으면 Redis에 적재하지 않음")
    void warmUpCache_dbEmpty_doesNotPushToRedis() {
        // given
        given(redisTemplate.hasKey(anyString())).willReturn(false);
        given(newsRepository.findTop50ByCategoryOrderByPublishedAtDesc(anyString()))
                .willReturn(List.of());

        // when
        newsRedisService.warmUpCache();

        // then
        then(listOps).should(never()).rightPushAll(anyString(), anyList());
        then(redisTemplate).should(never()).expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("워밍업 - 특정 카테고리 DB 조회 실패 시 나머지 카테고리는 계속 진행")
    void warmUpCache_oneDbFails_continuesOtherCategories() throws Exception {
        // given
        given(redisTemplate.hasKey(anyString())).willReturn(false);

        // "economy" 카테고리만 예외 발생
        given(newsRepository.findTop50ByCategoryOrderByPublishedAtDesc("economy"))
                .willThrow(new RuntimeException("DB timeout"));

        News news = News.builder().id(1L).title("기사").url("http://a.com")
                .keywords(null).build();
        given(newsRepository.findTop50ByCategoryOrderByPublishedAtDesc(
                argThat(c -> !c.equals("economy"))))
                .willReturn(List.of(news));
        given(objectMapper.writeValueAsString(any())).willReturn("{\"title\":\"기사\"}");

        // when: 예외가 전파되지 않아야 함
        newsRedisService.warmUpCache();

        // then: 실패한 economy 제외 5개만 적재
        then(listOps).should(times(5)).rightPushAll(anyString(), anyList());
    }
}
