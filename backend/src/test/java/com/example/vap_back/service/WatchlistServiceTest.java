package com.example.vap_back.service;

import com.example.vap_back.Entity.User;
import com.example.vap_back.Entity.Watchlist;
import com.example.vap_back.dto.WatchlistRequestDto;
import com.example.vap_back.exception.UserNotFoundException;
import com.example.vap_back.repository.UserRepository;
import com.example.vap_back.repository.WatchlistRepository;
import com.example.vap_back.service.impl.WatchlistServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class WatchlistServiceTest {

    @Mock WatchlistRepository watchlistRepository;
    @Mock UserRepository userRepository;

    @InjectMocks
    WatchlistServiceImpl watchlistService;

    private User testUser() {
        return User.builder().id(1L).email("user@test.com").password("enc").build();
    }

    private WatchlistRequestDto testDto(String symbol) {
        WatchlistRequestDto dto = new WatchlistRequestDto();
        dto.setSymbol(symbol);
        return dto;
    }

    @Test
    @DisplayName("관심종목 추가 - 신규 종목이면 저장 후 '관심종목에 추가됨' 반환")
    void addToWatchlist_new() {
        given(userRepository.findByEmail("user@test.com")).willReturn(Optional.of(testUser()));
        given(watchlistRepository.existsByUserIdAndSymbol(1L, "AAPL")).willReturn(false);

        String result = watchlistService.addToWatchlist("user@test.com", testDto("aapl"));

        assertThat(result).isEqualTo("관심종목에 추가됨");
        then(watchlistRepository).should().save(any(Watchlist.class));
    }

    @Test
    @DisplayName("관심종목 추가 - 이미 있으면 저장하지 않고 안내 메시지 반환")
    void addToWatchlist_alreadyExists() {
        given(userRepository.findByEmail("user@test.com")).willReturn(Optional.of(testUser()));
        given(watchlistRepository.existsByUserIdAndSymbol(1L, "AAPL")).willReturn(true);

        String result = watchlistService.addToWatchlist("user@test.com", testDto("AAPL"));

        assertThat(result).isEqualTo("이미 관심종목에 있습니다");
        then(watchlistRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("관심종목 추가 실패 - 없는 사용자면 UserNotFoundException")
    void addToWatchlist_userNotFound() {
        given(userRepository.findByEmail("none@test.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> watchlistService.addToWatchlist("none@test.com", testDto("AAPL")))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("관심종목 제거 - 정상 삭제 후 안내 메시지 반환")
    void removeFromWatchlist_success() {
        given(userRepository.findByEmail("user@test.com")).willReturn(Optional.of(testUser()));

        String result = watchlistService.removeFromWatchlist("user@test.com", "aapl");

        assertThat(result).isEqualTo("관심종목에서 제거됨");
        then(watchlistRepository).should().deleteByUserIdAndSymbol(1L, "AAPL");
    }

    @Test
    @DisplayName("내 관심종목 조회 성공 - addedAt 내림차순 목록 반환")
    void getMyWatchlist_success() {
        Watchlist w1 = Watchlist.builder().id(1L).userId(1L).symbol("AAPL")
                .addedAt(LocalDateTime.now()).build();
        Watchlist w2 = Watchlist.builder().id(2L).userId(1L).symbol("MSFT")
                .addedAt(LocalDateTime.now().minusDays(1)).build();
        given(userRepository.findByEmail("user@test.com")).willReturn(Optional.of(testUser()));
        given(watchlistRepository.findAllByUserIdOrderByAddedAtDesc(1L)).willReturn(List.of(w1, w2));

        List<Watchlist> result = watchlistService.getMyWatchlist("user@test.com");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSymbol()).isEqualTo("AAPL");
    }

    @Test
    @DisplayName("내 관심종목 조회 실패 - 없는 사용자면 UserNotFoundException")
    void getMyWatchlist_userNotFound() {
        given(userRepository.findByEmail("none@test.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> watchlistService.getMyWatchlist("none@test.com"))
                .isInstanceOf(UserNotFoundException.class);
    }
}
