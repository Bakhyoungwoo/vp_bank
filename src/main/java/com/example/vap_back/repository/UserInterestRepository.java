package com.example.vap_back.repository;

import com.example.vap_back.Entity.User;
import com.example.vap_back.Entity.UserInterest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserInterestRepository extends JpaRepository<UserInterest, Long> {

    // 특정 사용자가 등록한 모든 관심 카테고리 리스트
    List<UserInterest> findByUser(User user);

    // 관심사 변경 시 기존 설정을 삭제
    void deleteByUser(User user);
}