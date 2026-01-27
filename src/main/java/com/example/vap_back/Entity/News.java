package com.example.vap_back.Entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "news",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "url")
        },
        // 카테고리별 조회, 시간순 조회를 빠르게 하기 위한 인덱스 설정
        indexes = {
                @Index(name = "idx_category", columnList = "category"),
                @Index(name = "idx_published_at", columnList = "publishedAt")
        }
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class News {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String category;

    @Column(nullable = false, length = 255)
    private String title;

    @Lob
    @Column(columnDefinition = "MEDIUMTEXT")
    private String content;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(length = 100)
    private String press;

    private LocalDateTime publishedAt;

    @Column(columnDefinition = "TEXT")
    private String keywords;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }
}
