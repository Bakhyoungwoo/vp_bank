package com.example.vap_back.controller;

import com.example.vap_back.Entity.Watchlist;
import com.example.vap_back.dto.WatchlistRequestDto;
import com.example.vap_back.service.WatchlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/watchlist")
@RequiredArgsConstructor
@Tag(name = "Watchlist", description = "관심종목 추가/삭제/조회")
public class WatchlistController {

    private final WatchlistService watchlistService;

    @Operation(summary = "관심종목 추가", description = "종목을 관심종목에 추가합니다. 이미 있으면 그대로 유지합니다.")
    @PostMapping
    public String addToWatchlist(@RequestBody WatchlistRequestDto dto, Authentication authentication) {
        return watchlistService.addToWatchlist(authentication.getName(), dto);
    }

    @Operation(summary = "관심종목 삭제", description = "종목을 관심종목에서 제거합니다.")
    @DeleteMapping("/{symbol}")
    public String removeFromWatchlist(@PathVariable String symbol, Authentication authentication) {
        return watchlistService.removeFromWatchlist(authentication.getName(), symbol);
    }

    @Operation(summary = "내 관심종목 조회", description = "인증된 사용자의 관심종목 목록을 추가일 내림차순으로 반환합니다.")
    @GetMapping
    public List<Watchlist> getMyWatchlist(Authentication authentication) {
        return watchlistService.getMyWatchlist(authentication.getName());
    }
}
