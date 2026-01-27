package com.example.vap_back.repository;

import com.example.vap_back.Entity.News;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NewsRepository extends JpaRepository<News, Long> {

    boolean existsByUrl(String url);

    List<News> findTop50ByCategoryOrderByPublishedAtDesc(String category);
    List<News> findByTitleContainingOrderByPublishedAtDesc(String keyword);
}
