package com.example.vap_back.repository;

import com.example.vap_back.Entity.Watchlist;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.List;
import java.util.Optional;

public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {

    @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
    List<Watchlist> findAllByUserIdOrderByAddedAtDesc(Long userId);

    @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
    Optional<Watchlist> findByUserIdAndSymbol(Long userId, String symbol);

    boolean existsByUserIdAndSymbol(Long userId, String symbol);

    void deleteByUserIdAndSymbol(Long userId, String symbol);
}
