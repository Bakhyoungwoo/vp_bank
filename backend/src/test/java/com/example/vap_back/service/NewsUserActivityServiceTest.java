package com.example.vap_back.service;

import com.example.vap_back.service.impl.NewsUserActivityServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class NewsUserActivityServiceTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ZSetOperations<String, String> zSetOps;

    @InjectMocks
    NewsUserActivityServiceImpl newsUserActivityService;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForZSet()).willReturn(zSetOps);
    }

    @Test
    @DisplayName("클릭 로그 추가 - 유효 키워드의 점수 증가 및 30일 만료 설정")
    void addClickLog_validKeywords_incrementsScore() {
        // when
        newsUserActivityService.addClickLog(1L, List.of("AI", "Spring", "Kafka"));

        // then
        then(zSetOps).should().incrementScore("user:1:keywords", "AI", 1.0);
        then(zSetOps).should().incrementScore("user:1:keywords", "Spring", 1.0);
        then(zSetOps).should().incrementScore("user:1:keywords", "Kafka", 1.0);
        then(redisTemplate).should().expire("user:1:keywords", Duration.ofDays(30));
    }

    @Test
    @DisplayName("클릭 로그 추가 - 빈 문자열 키워드는 무시")
    void addClickLog_blankKeyword_ignored() {
        // when
        newsUserActivityService.addClickLog(1L, List.of("AI", "", "  "));

        // then: "AI"만 incrementScore 호출, blank는 무시
        then(zSetOps).should(times(1)).incrementScore(anyString(), anyString(), anyDouble());
    }

    @Test
    @DisplayName("관심사 TOP N 조회 - Redis 정렬 집합에서 반환")
    void getTopInterests_returnsFromRedis() {
        // given
        Set<String> expected = new LinkedHashSet<>(List.of("AI", "Spring"));
        given(zSetOps.reverseRange("user:1:keywords", 0, 4)).willReturn(expected);

        // when
        Set<String> result = newsUserActivityService.getTopInterests(1L, 5);

        // then
        assertThat(result).containsExactlyInAnyOrder("AI", "Spring");
    }

    @Test
    @DisplayName("관심사 점수 포함 조회 - Map<String,Double> 올바르게 변환")
    void getTopInterestsWithScores_returnsMap() {
        // given
        @SuppressWarnings("unchecked")
        ZSetOperations.TypedTuple<String> tuple = mock(ZSetOperations.TypedTuple.class);
        given(tuple.getValue()).willReturn("AI");
        given(tuple.getScore()).willReturn(10.0);

        Set<ZSetOperations.TypedTuple<String>> tuples = new LinkedHashSet<>(Set.of(tuple));
        given(zSetOps.reverseRangeWithScores("user:1:keywords", 0, 4)).willReturn(tuples);

        // when
        Map<String, Double> result = newsUserActivityService.getTopInterestsWithScores(1L, 5);

        // then
        assertThat(result).containsEntry("AI", 10.0);
    }
}
