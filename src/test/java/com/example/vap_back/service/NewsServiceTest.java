package com.example.vap_back.service;

import com.example.vap_back.dto.NewsCreateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class NewsServiceTest {

    @Autowired
    private NewsService newsService;

    @Test
    @DisplayName("뉴스 저장 로직 성능 및 중복 체크 속도 측정")
    void saveNewsPerformanceTest() {
        // 1. Builder 패턴으로 데이터 생성 (에러 해결)
        NewsCreateRequest request = NewsCreateRequest.builder()
                .category("it")
                .title("테스트 뉴스 제목")
                .content("테스트 본문 내용")
                .url("http://test-url.com/" + System.currentTimeMillis())
                .press("테스트 언론사")
                .publishedAt(LocalDateTime.now())
                .keywords(List.of("AI", "Spring", "Kafka"))
                .build();

        // 2. 실행 및 AOP 성능 로그 확인
        newsService.saveNews(request);

        // 3. 중복 저장 시도 (AOP 로그: [NEWS SKIP] 로그 확인 및 시간 측정)
        newsService.saveNews(request);
    }

    @Test
    @DisplayName("DB 카테고리 조회 성능 측정")
    void getCategoryPerformanceTest() {
        // 실행 (AOP 로그: [EXECUTION] NewsService.getNewsByCategory 확인)
        var results = newsService.getNewsByCategory("it");

        assertNotNull(results);
        System.out.println("조회된 뉴스 개수: " + results.size());
    }
}