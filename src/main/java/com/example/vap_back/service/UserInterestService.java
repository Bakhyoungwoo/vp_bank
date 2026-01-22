package com.example.vap_back.service;

import com.example.vap_back.Entity.User;
import com.example.vap_back.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserInterestService {
    private final UserRepository userRepository;

    // email로 User 조회
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException("User not found: " + email)
                );
    }
}
