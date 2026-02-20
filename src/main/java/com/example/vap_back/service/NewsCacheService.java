package com.example.vap_back.service;

import java.util.List;
import java.util.Map;

public interface NewsCacheService {
    List<Map<String, Object>> getLatestArticles(String category, int limit);
    void crawlAndSave(String category, List<Map<String, Object>> articles);
}
