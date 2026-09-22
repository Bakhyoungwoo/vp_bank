package com.example.vap_back.repository;

import com.example.vap_back.Entity.BriefingHistory;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.List;
import java.util.Optional;

public interface BriefingHistoryRepository extends JpaRepository<BriefingHistory, Long> {

    @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
    Optional<BriefingHistory> findTopByUserIdOrderByGeneratedAtDesc(Long userId);

    @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
    List<BriefingHistory> findAllByUserIdOrderByGeneratedAtDesc(Long userId);
}
