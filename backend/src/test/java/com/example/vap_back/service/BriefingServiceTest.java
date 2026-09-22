package com.example.vap_back.service;

import com.example.vap_back.Entity.BriefingHistory;
import com.example.vap_back.Entity.User;
import com.example.vap_back.Entity.Watchlist;
import com.example.vap_back.repository.BriefingHistoryRepository;
import com.example.vap_back.repository.UserRepository;
import com.example.vap_back.repository.WatchlistRepository;
import com.example.vap_back.service.impl.BriefingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class BriefingServiceTest {

    @Mock WatchlistRepository watchlistRepository;
    @Mock BriefingHistoryRepository briefingHistoryRepository;
    @Mock UserRepository userRepository;
    @Mock RestTemplate restTemplate;

    @InjectMocks
    BriefingServiceImpl briefingService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(briefingService, "aiBaseUrl", "http://localhost:8000");
    }

    private User testUser() {
        return User.builder().id(1L).email("user@test.com").password("enc").build();
    }

    @Test
    @DisplayName("브리핑 생성 실패 - 관심종목이 없으면 400")
    void generateBriefing_emptyWatchlist_returns400() {
        given(userRepository.findByEmail("user@test.com")).willReturn(Optional.of(testUser()));
        given(watchlistRepository.findAllByUserIdOrderByAddedAtDesc(1L)).willReturn(List.of());

        ResponseEntity<Map<String, Object>> response = briefingService.generateBriefing("user@test.com");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        then(restTemplate).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("브리핑 생성 성공 - 이전 이력이 없으면 기본 7일 기준으로 조회하고 이력을 저장한다")
    void generateBriefing_success_usesDefaultSinceDaysAndSavesHistory() {
        Watchlist watchlist = Watchlist.builder().id(1L).userId(1L).symbol("AAPL")
                .addedAt(LocalDateTime.now()).build();
        given(userRepository.findByEmail("user@test.com")).willReturn(Optional.of(testUser()));
        given(watchlistRepository.findAllByUserIdOrderByAddedAtDesc(1L)).willReturn(List.of(watchlist));
        given(briefingHistoryRepository.findTopByUserIdOrderByGeneratedAtDesc(1L)).willReturn(Optional.empty());
        given(restTemplate.postForObject(any(URI.class), isNull(), eq(Map.class)))
                .willReturn(Map.of("status", "ready", "llmNarrative", "요약"));

        ResponseEntity<Map<String, Object>> response = briefingService.generateBriefing("user@test.com");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("status", "ready");
        then(briefingHistoryRepository).should().save(any(BriefingHistory.class));

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        then(restTemplate).should().postForObject(uriCaptor.capture(), isNull(), eq(Map.class));
        assertThat(uriCaptor.getValue().toString()).contains("symbols=AAPL").contains("days=7");
    }

    @Test
    @DisplayName("브리핑 생성 - AI 서비스 장애 시 502를 반환하고 이력을 저장하지 않는다")
    void generateBriefing_aiServiceUnavailable_returns502() {
        Watchlist watchlist = Watchlist.builder().id(1L).userId(1L).symbol("AAPL")
                .addedAt(LocalDateTime.now()).build();
        given(userRepository.findByEmail("user@test.com")).willReturn(Optional.of(testUser()));
        given(watchlistRepository.findAllByUserIdOrderByAddedAtDesc(1L)).willReturn(List.of(watchlist));
        given(briefingHistoryRepository.findTopByUserIdOrderByGeneratedAtDesc(1L)).willReturn(Optional.empty());
        given(restTemplate.postForObject(any(URI.class), isNull(), eq(Map.class)))
                .willThrow(new RuntimeException("connection refused"));

        ResponseEntity<Map<String, Object>> response = briefingService.generateBriefing("user@test.com");

        assertThat(response.getStatusCode().value()).isEqualTo(502);
        then(briefingHistoryRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("내 브리핑 이력 조회 - 최신순 목록 반환")
    void getMyBriefingHistory_success() {
        BriefingHistory history = BriefingHistory.builder()
                .id(1L).userId(1L).generatedAt(LocalDateTime.now()).symbols("AAPL").status("ready").build();
        given(userRepository.findByEmail("user@test.com")).willReturn(Optional.of(testUser()));
        given(briefingHistoryRepository.findAllByUserIdOrderByGeneratedAtDesc(1L)).willReturn(List.of(history));

        List<BriefingHistory> result = briefingService.getMyBriefingHistory("user@test.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSymbols()).isEqualTo("AAPL");
    }
}
