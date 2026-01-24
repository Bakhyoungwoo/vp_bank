package com.example.vap_back.service;

import com.example.vap_back.Entity.News;
import com.example.vap_back.dto.NewsCreateRequest;
import com.example.vap_back.repository.NewsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class NewsService {

    private final NewsRepository newsRepository;

    // 카테고리별 뉴스 (DB)
    public List<News> getNewsByCategory(String category) {
        return newsRepository
                .findTop50ByCategoryOrderByPublishedAtDesc(category);
    }

    // FastAPI → Spring 저장
    public void saveNews(NewsCreateRequest request) {

        if (newsRepository.existsByUrl(request.getUrl())) {
            log.debug("[NEWS SKIP] already exists: {}", request.getUrl());
            return;
        }

        News news = News.builder()
                .category(request.getCategory())
                .title(request.getTitle())
                .content(request.getContent())
                .url(request.getUrl())
                .press(request.getPress())
                .publishedAt(request.getPublishedAt())
                .build();

        newsRepository.save(news);
        log.info("[NEWS SAVED] {} / {}", request.getCategory(), request.getTitle());
    }
}
