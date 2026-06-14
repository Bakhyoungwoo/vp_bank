package com.example.vap_back.service;

import com.example.vap_back.Entity.News;
import com.example.vap_back.dto.NewsCreateRequest;
import com.example.vap_back.repository.NewsRepository;
import com.example.vap_back.service.impl.NewsServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class NewsServiceTest {

    @Mock NewsRepository newsRepository;
    @Mock ObjectMapper objectMapper;

    @InjectMocks
    NewsServiceImpl newsService;

    @Test
    @DisplayName("카테고리별 뉴스 조회 - 최신 50개 반환")
    void getNewsByCategory_returnsResults() {
        // given
        News n1 = News.builder().id(1L).category("it").title("AI 뉴스").url("http://a.com").build();
        News n2 = News.builder().id(2L).category("it").title("Spring 뉴스").url("http://b.com").build();
        given(newsRepository.findTop50ByCategoryOrderByPublishedAtDesc("it")).willReturn(List.of(n1, n2));

        // when
        List<News> result = newsService.getNewsByCategory("it");

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTitle()).isEqualTo("AI 뉴스");
    }

    @Test
    @DisplayName("뉴스 검색 - 키워드 포함 뉴스 목록 반환")
    void searchNews_returnsResults() {
        // given
        News news = News.builder().id(1L).title("AI 최신 트렌드").url("http://ai.com").build();
        given(newsRepository.searchByKeyword("AI")).willReturn(List.of(news));

        // when
        List<News> result = newsService.searchNews("AI");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).contains("AI");
    }

    @Test
    @DisplayName("뉴스 저장 성공 - 새 URL이면 DB에 저장")
    void saveNews_newUrl_savedSuccessfully() {
        // given
        NewsCreateRequest request = mock(NewsCreateRequest.class);
        given(request.getUrl()).willReturn("http://new-article.com");
        given(request.getKeywords()).willReturn(null);
        given(newsRepository.existsByUrl("http://new-article.com")).willReturn(false);

        // when
        newsService.saveNews(request);

        // then
        then(newsRepository).should().save(any(News.class));
    }

    @Test
    @DisplayName("뉴스 저장 스킵 - 중복 URL이면 저장하지 않음")
    void saveNews_duplicateUrl_skipped() {
        // given
        NewsCreateRequest request = mock(NewsCreateRequest.class);
        given(request.getUrl()).willReturn("http://duplicate.com");
        given(newsRepository.existsByUrl("http://duplicate.com")).willReturn(true);

        // when
        newsService.saveNews(request);

        // then
        then(newsRepository).should(never()).save(any(News.class));
    }

    @Test
    @DisplayName("뉴스 저장 - keywords가 null이면 '[]'로 저장")
    void saveNews_nullKeywords_storedAsEmptyJson() {
        // given
        NewsCreateRequest request = mock(NewsCreateRequest.class);
        given(request.getUrl()).willReturn("http://no-keywords.com");
        given(request.getKeywords()).willReturn(null);
        given(newsRepository.existsByUrl("http://no-keywords.com")).willReturn(false);

        // when
        newsService.saveNews(request);

        // then: keywords null → objectMapper 미호출, news.keywords = "[]"
        then(objectMapper).shouldHaveNoInteractions();
        then(newsRepository).should().save(argThat(news -> "[]".equals(news.getKeywords())));
    }
}
