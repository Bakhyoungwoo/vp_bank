package com.example.vap_back.service.impl;

import com.example.vap_back.Entity.Watchlist;
import com.example.vap_back.dto.WatchlistRequestDto;
import com.example.vap_back.exception.UserNotFoundException;
import com.example.vap_back.repository.UserRepository;
import com.example.vap_back.repository.WatchlistRepository;
import com.example.vap_back.service.WatchlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WatchlistServiceImpl implements WatchlistService {

    private final WatchlistRepository watchlistRepository;
    private final UserRepository userRepository;

    @Transactional
    @Override
    public String addToWatchlist(String email, WatchlistRequestDto dto) {
        Long userId = resolveUserId(email);
        String symbol = dto.getSymbol().trim().toUpperCase();

        if (watchlistRepository.existsByUserIdAndSymbol(userId, symbol)) {
            return "이미 관심종목에 있습니다";
        }
        watchlistRepository.save(Watchlist.builder()
                .userId(userId)
                .symbol(symbol)
                .addedAt(LocalDateTime.now())
                .build());
        return "관심종목에 추가됨";
    }

    @Transactional
    @Override
    public String removeFromWatchlist(String email, String symbol) {
        Long userId = resolveUserId(email);
        watchlistRepository.deleteByUserIdAndSymbol(userId, symbol.trim().toUpperCase());
        return "관심종목에서 제거됨";
    }

    @Override
    public List<Watchlist> getMyWatchlist(String email) {
        Long userId = resolveUserId(email);
        return watchlistRepository.findAllByUserIdOrderByAddedAtDesc(userId);
    }

    private Long resolveUserId(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email))
                .getId();
    }
}
