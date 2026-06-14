package com.example.vap_back.service;

import com.example.vap_back.service.impl.NewsTrendingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class NewsTrendingServiceTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ZSetOperations<String, String> zSetOps;

    @InjectMocks
    NewsTrendingServiceImpl newsTrendingService;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForZSet()).willReturn(zSetOps);
    }

    @Test
    @DisplayName("트렌딩 키워드 조회 - keyword/score 포함 리스트 반환")
    void getTopKeywords_withData_returnsList() {
        // given
        @SuppressWarnings("unchecked")
        ZSetOperations.TypedTuple<String> t1 = mock(ZSetOperations.TypedTuple.class);
        given(t1.getValue()).willReturn("AI");
        given(t1.getScore()).willReturn(50.0);

        @SuppressWarnings("unchecked")
        ZSetOperations.TypedTuple<String> t2 = mock(ZSetOperations.TypedTuple.class);
        given(t2.getValue()).willReturn("반도체");
        given(t2.getScore()).willReturn(30.0);

        Set<ZSetOperations.TypedTuple<String>> tuples = new LinkedHashSet<>(List.of(t1, t2));
        given(zSetOps.reverseRangeWithScores("trend:it:scores", 0, 9)).willReturn(tuples);

        // when
        List<Map<String, Object>> result = newsTrendingService.getTopKeywords("it");

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsEntry("keyword", "AI");
        assertThat(result.get(0)).containsEntry("score", 50);
        assertThat(result.get(1)).containsEntry("keyword", "반도체");
    }

    @Test
    @DisplayName("트렌딩 키워드 조회 - 데이터 없으면 빈 리스트 반환")
    void getTopKeywords_noData_returnsEmptyList() {
        // given
        given(zSetOps.reverseRangeWithScores("trend:economy:scores", 0, 9)).willReturn(null);

        // when
        List<Map<String, Object>> result = newsTrendingService.getTopKeywords("economy");

        // then
        assertThat(result).isEmpty();
    }
}
