package com.example.vap_back.service;

import java.util.List;
import java.util.Map;

public interface NewsRecommendationService {
    List<Map<String, Object>> recommendArticles(Long userId, int limit);
}
