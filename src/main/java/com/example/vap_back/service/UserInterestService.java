package com.example.vap_back.service;

import com.example.vap_back.Entity.User;
import com.example.vap_back.Entity.UserInterest;
import com.example.vap_back.repository.UserInterestRepository;
import com.example.vap_back.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserInterestService {

    private final UserInterestRepository userInterestRepository;
    private final UserRepository userRepository;

    // email로 User 조회
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException("User not found: " + email)
                );
    }

    // 단일 관심사 추가 (POST /api/interests)
    @Transactional
    public void addInterest(User user, String category) {
        if (userInterestRepository.existsByUserAndCategory(user, category)) {
            return;
        }

        UserInterest interest = new UserInterest(user, category);
        userInterestRepository.save(interest);
    }

    // 관심사 전체 업데이트 (PUT)
    @Transactional
    public void updateUserInterests(User user, List<String> categories) {
        userInterestRepository.deleteByUser(user);

        List<UserInterest> interests = categories.stream()
                .map(cat -> new UserInterest(user, cat))
                .collect(Collectors.toList());

        userInterestRepository.saveAll(interests);
    }

    // 관심사 조회
    public List<String> getUserCategoryList(User user) {
        return userInterestRepository.findByUser(user).stream()
                .map(UserInterest::getCategory)
                .collect(Collectors.toList());
    }
}
