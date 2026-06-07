package com.example.vap_back.repository;

import com.example.vap_back.Entity.News;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NewsRepository extends JpaRepository<News, Long> {

    boolean existsByUrl(String url);

    // readOnly 힌트: 영속성 컨텍스트 dirty checking 비활성화 → 조회 성능 향상
    @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
    List<News> findTop50ByCategoryOrderByPublishedAtDesc(String category);

    // 제목 + 내용 통합 검색, readOnly 힌트 적용
    @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
    @Query("SELECT n FROM News n WHERE n.title LIKE %:keyword% OR n.content LIKE %:keyword% ORDER BY n.publishedAt DESC")
    List<News> searchByKeyword(@Param("keyword") String keyword);
}
