package com.example.vap_back.repository;

import com.example.vap_back.Entity.Bookmark;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {
    List<Bookmark> findAllByUserIdOrderBySavedAtDesc(Long userId);
    Optional<Bookmark> findByUserIdAndNewsUrl(Long userId, String newsUrl);
    boolean existsByUserIdAndNewsUrl(Long userId, String newsUrl);
}