package com.example.vap_back.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Bookmark {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 누가 저장했는지
    @Column(nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "news_id", nullable = false)
    @JsonIgnore
    private News news;

    // 기존 클라이언트 응답과 테스트 호환을 위한 읽기 전용 필드
    @Transient
    private String newsUrl;
    @Transient
    private String title;
    @Transient
    private String press;
    @Transient
    private String publishedAt;

    private LocalDateTime savedAt;

    public String getNewsUrl() {
        return news != null ? news.getUrl() : newsUrl;
    }

    public String getTitle() {
        return news != null ? news.getTitle() : title;
    }

    public String getPress() {
        return news != null ? news.getPress() : press;
    }

    public String getPublishedAt() {
        return news != null && news.getPublishedAt() != null
                ? news.getPublishedAt().toString()
                : publishedAt;
    }
}
