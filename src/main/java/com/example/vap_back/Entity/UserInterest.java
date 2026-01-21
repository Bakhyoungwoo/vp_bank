package com.example.vap_back.Entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_interests")
@Getter @Setter
@NoArgsConstructor
public class UserInterest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 기존 User 엔티티와의 연관(로그인한 사용자)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // 관심 카테고리 (it, economy, society, politics, life, world 등)
    @Column(nullable = false)
    private String category;

    // 생성자 편의 메소드
    public UserInterest(User user, String category) {
        this.user = user;
        this.category = category;
    }
}