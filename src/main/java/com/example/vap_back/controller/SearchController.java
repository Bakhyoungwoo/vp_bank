package com.example.vap_back.controller;

import com.example.vap_back.Entity.User;
import com.example.vap_back.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "검색 히스토리 관리")
public class SearchController {

    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;

    private boolean isAnonymous(Authentication authentication) {
        return authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName());
    }

    @Operation(summary = "검색어 저장", description = "사용자의 검색어를 Redis에 히스토리로 저장합니다. (최대 5건 유지)")
    @PostMapping
    public void saveSearchHistory(@RequestBody String keyword, Authentication authentication) {
        if (isAnonymous(authentication)) return;

        User user = userRepository.findByEmail(authentication.getName()).orElse(null);
        if (user == null) return;

        String key = "search:history:" + user.getId();
        redisTemplate.opsForList().remove(key, 0, keyword);
        redisTemplate.opsForList().leftPush(key, keyword);
        redisTemplate.opsForList().trim(key, 0, 4);
    }

    @Operation(summary = "검색 히스토리 조회", description = "인증된 사용자의 최근 검색어 목록을 반환합니다.")
    @GetMapping("/history")
    public List<String> getSearchHistory(Authentication authentication) {
        if (isAnonymous(authentication)) return Collections.emptyList();

        User user = userRepository.findByEmail(authentication.getName()).orElse(null);
        if (user == null) return Collections.emptyList();

        return redisTemplate.opsForList().range("search:history:" + user.getId(), 0, -1);
    }

    @Operation(summary = "검색 히스토리 삭제", description = "인증된 사용자의 전체 검색 히스토리를 삭제합니다.")
    @DeleteMapping("/history")
    public void deleteHistory(Authentication authentication) {
        if (isAnonymous(authentication)) return;

        userRepository.findByEmail(authentication.getName()).ifPresent(user ->
                redisTemplate.delete("search:history:" + user.getId())
        );
    }
}
