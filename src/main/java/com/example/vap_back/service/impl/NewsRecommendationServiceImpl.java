package com.example.vap_back.service.impl;

import com.example.vap_back.service.NewsCacheService;
import com.example.vap_back.service.NewsRecommendationService;
import com.example.vap_back.service.NewsUserActivityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsRecommendationServiceImpl implements NewsRecommendationService {

    private final NewsCacheService newsCacheService;
    private final NewsUserActivityService newsUserActivityService;

    private static final List<String> CATEGORIES =
            List.of("economy", "society", "it", "politics", "world", "culture");
    private static final int ARTICLES_PER_CATEGORY = 50;
    private static final int TOP_USER_INTERESTS = 20;

    @Override
    public List<Map<String, Object>> recommendArticles(Long userId, int limit) {
        Map<String, Double> userKeywordScore =
                newsUserActivityService.getTopInterestsWithScores(userId, TOP_USER_INTERESTS);

        if (userKeywordScore.isEmpty()) {
            return newsCacheService.getLatestArticles("it", limit);
        }

        List<Map<String, Object>> candidateArticles = new ArrayList<>();
        for (String category : CATEGORIES) {
            candidateArticles.addAll(newsCacheService.getLatestArticles(category, ARTICLES_PER_CATEGORY));
        }

        List<Map<String, Object>> scoredArticles = new ArrayList<>();

        for (Map<String, Object> article : candidateArticles) {
            Object kwObj = article.get("keywords");
            if (!(kwObj instanceof Collection<?> articleKeywords)) continue;

            double totalScore = 0.0;
            List<String> matched = new ArrayList<>();

            for (Object k : articleKeywords) {
                String keyword = String.valueOf(k).trim();
                if (userKeywordScore.containsKey(keyword)) {
                    totalScore += userKeywordScore.get(keyword);
                    matched.add(keyword);
                }
            }

            if (totalScore > 0) {
                Map<String, Object> result = new HashMap<>(article);
                result.put("score", totalScore);
                result.put("matchedKeywords", matched);
                scoredArticles.add(result);
            }
        }

        if (scoredArticles.isEmpty()) {
            return newsCacheService.getLatestArticles("it", limit);
        }

        return scoredArticles.stream()
                .sorted((a, b) -> {
                    double scoreA = ((Number) a.get("score")).doubleValue();
                    double scoreB = ((Number) b.get("score")).doubleValue();
                    return Double.compare(scoreB, scoreA);
                })
                .limit(limit)
                .collect(Collectors.toList());
    }
}
