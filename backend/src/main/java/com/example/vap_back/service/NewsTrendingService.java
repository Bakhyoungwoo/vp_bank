package com.example.vap_back.service;

import java.util.List;
import java.util.Map;

public interface NewsTrendingService {
    List<Map<String, Object>> getTopKeywords(String category);
}
