package com.example.vap_back.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface NewsUserActivityService {
    void addClickLog(Long userId, List<String> keywords);
    Set<String> getTopInterests(Long userId, int limit);
    Map<String, Double> getTopInterestsWithScores(Long userId, int limit);
}
