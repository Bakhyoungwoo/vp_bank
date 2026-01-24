package com.example.vap_back.repository;

import com.example.vap_back.Entity.News;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NewsRepository extends JpaRepository<News, Long> {

    Optional<News> findByUrl(String url);

    boolean existsByUrl(String url);

    List<News> findTop50ByCategoryOrderByPublishedAtDesc(String category);
}
