package com.example.vap_back.service.impl;

import com.example.vap_back.Entity.BriefingHistory;
import com.example.vap_back.Entity.Watchlist;
import com.example.vap_back.exception.UserNotFoundException;
import com.example.vap_back.repository.BriefingHistoryRepository;
import com.example.vap_back.repository.UserRepository;
import com.example.vap_back.repository.WatchlistRepository;
import com.example.vap_back.service.BriefingService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BriefingServiceImpl implements BriefingService {

    private static final int DEFAULT_SINCE_DAYS = 7;
    private static final int MIN_SINCE_DAYS = 1;
    private static final int MAX_SINCE_DAYS = 90;

    private final WatchlistRepository watchlistRepository;
    private final BriefingHistoryRepository briefingHistoryRepository;
    private final UserRepository userRepository;
    private final RestTemplate restTemplate;

    @Value("${ai.base-url:http://localhost:8000}")
    private String aiBaseUrl;

    @Transactional
    @Override
    public ResponseEntity<Map<String, Object>> generateBriefing(String email) {
        Long userId = resolveUserId(email);

        List<Watchlist> watchlist = watchlistRepository.findAllByUserIdOrderByAddedAtDesc(userId);
        if (watchlist.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "관심종목이 없습니다"));
        }
        String symbols = watchlist.stream().map(Watchlist::getSymbol).collect(Collectors.joining(","));
        int sinceDays = resolveSinceDays(userId);

        URI url = UriComponentsBuilder.fromHttpUrl(aiBaseUrl + "/ai/briefing")
                .queryParam("symbols", symbols)
                .queryParam("days", sinceDays)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();
        try {
            Map<String, Object> result = restTemplate.postForObject(url, null, Map.class);
            if (result == null) {
                return ResponseEntity.status(502).body(Map.of(
                        "status", "unavailable", "message", "AI briefing service unavailable"));
            }
            briefingHistoryRepository.save(BriefingHistory.builder()
                    .userId(userId)
                    .generatedAt(LocalDateTime.now())
                    .symbols(symbols)
                    .status((String) result.get("status"))
                    .llmNarrative((String) result.get("llmNarrative"))
                    .build());
            return ResponseEntity.ok(result);
        } catch (Exception exception) {
            return ResponseEntity.status(502).body(Map.of(
                    "status", "unavailable",
                    "message", "AI briefing service unavailable"));
        }
    }

    @Override
    public List<BriefingHistory> getMyBriefingHistory(String email) {
        Long userId = resolveUserId(email);
        return briefingHistoryRepository.findAllByUserIdOrderByGeneratedAtDesc(userId);
    }

    /** 마지막 브리핑 시점 이후 경과일을 계산한다. 이력이 없으면 기본값을 사용한다. */
    private int resolveSinceDays(Long userId) {
        return briefingHistoryRepository.findTopByUserIdOrderByGeneratedAtDesc(userId)
                .map(last -> {
                    long elapsed = Duration.between(last.getGeneratedAt(), LocalDateTime.now()).toDays();
                    return (int) Math.max(MIN_SINCE_DAYS, Math.min(MAX_SINCE_DAYS, elapsed));
                })
                .orElse(DEFAULT_SINCE_DAYS);
    }

    private Long resolveUserId(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email))
                .getId();
    }
}
