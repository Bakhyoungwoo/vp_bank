package com.example.vap_back.controller;

import com.example.vap_back.Entity.User;
import com.example.vap_back.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;

    // 검색어 저장
    @PostMapping
    public void saveSearchHistory(@RequestBody String keyword) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        // 비로그인 유저는 저장 안 함
        if (email.equals("anonymousUser")) return;

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return;

        String key = "search:history:" + user.getId();
        redisTemplate.opsForList().remove(key, 0, keyword); // 기존에 있으면 지우고
        redisTemplate.opsForList().leftPush(key, keyword);  // 맨 앞에 추가

        // 최근 5개만 유지
        redisTemplate.opsForList().trim(key, 0, 4);
    }

    // 최근 검색어 조회
    @GetMapping("/history")
    public List<String> getSearchHistory() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        if (email.equals("anonymousUser")) return Collections.emptyList();

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return Collections.emptyList();

        String key = "search:history:" + user.getId();
        // 전체 조회 (0 ~ -1)
        return redisTemplate.opsForList().range(key, 0, -1);
    }

    // 기록 삭제
    @DeleteMapping("/history")
    public void deleteHistory() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        userRepository.findByEmail(email).ifPresent(user ->
                redisTemplate.delete("search:history:" + user.getId())
        );
    }
}