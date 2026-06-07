package com.example.vap_back.repository;

import com.example.vap_back.Entity.Bookmark;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.List;
import java.util.Optional;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
    List<Bookmark> findAllByUserIdOrderBySavedAtDesc(Long userId);

    @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
    Optional<Bookmark> findByUserIdAndNewsUrl(Long userId, String newsUrl);

    boolean existsByUserIdAndNewsUrl(Long userId, String newsUrl);
}
