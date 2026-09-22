package com.example.vap_back.service;

import com.example.vap_back.Entity.Watchlist;
import com.example.vap_back.dto.WatchlistRequestDto;

import java.util.List;

public interface WatchlistService {
    String addToWatchlist(String email, WatchlistRequestDto dto);
    String removeFromWatchlist(String email, String symbol);
    List<Watchlist> getMyWatchlist(String email);
}
