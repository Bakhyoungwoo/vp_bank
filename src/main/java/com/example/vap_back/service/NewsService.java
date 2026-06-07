package com.example.vap_back.service;

import com.example.vap_back.Entity.News;
import com.example.vap_back.dto.NewsCreateRequest;

import java.util.List;

public interface NewsService {
    List<News> getNewsByCategory(String category);
    List<News> searchNews(String keyword);
    void saveNews(NewsCreateRequest request);
}
