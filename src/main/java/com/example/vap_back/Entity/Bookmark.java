package com.example.vap_back.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

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

    // 뉴스 정보 (별도 News 테이블과 조인하지 않고, 단순하게 정보만 저장)
    @Column(nullable = false, length = 1000)
    private String newsUrl;

    private String title;
    private String press;
    private String publishedAt;

    private LocalDateTime savedAt;
}