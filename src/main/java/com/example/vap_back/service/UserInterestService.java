package com.example.vap_back.service;

import com.example.vap_back.Entity.User;
import com.example.vap_back.Entity.UserInterest;
import com.example.vap_back.repository.UserInterestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserInterestService {

    private final UserInterestRepository userInterestRepository;

    // 사용자의 관심 카테고리 업데이트
    @Transactional
    public void updateUserInterests(User user, List<String> categories) {
        // 1. 기존 관심사 삭제 (초기화 후 재설정 방식)
        userInterestRepository.deleteByUser(user);

        // 2. 새로운 관심사 저장
        List<UserInterest> interests = categories.stream()
                .map(cat -> new UserInterest(user, cat))
                .collect(Collectors.toList());

        userInterestRepository.saveAll(interests);
    }

    // 사용자의 관심 카테고리 목록 조회
    public List<String> getUserCategoryList(User user) {
        return userInterestRepository.findByUser(user).stream()
                .map(UserInterest::getCategory)
                .collect(Collectors.toList());
    }
}