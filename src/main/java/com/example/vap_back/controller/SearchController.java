package com.example.vap_back.controller;

import com.example.vap_back.Entity.User;
import com.example.vap_back.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;

    private boolean isAnonymous(Authentication authentication) {
        return authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName());
    }

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

    @GetMapping("/history")
    public List<String> getSearchHistory(Authentication authentication) {
        if (isAnonymous(authentication)) return Collections.emptyList();

        User user = userRepository.findByEmail(authentication.getName()).orElse(null);
        if (user == null) return Collections.emptyList();

        return redisTemplate.opsForList().range("search:history:" + user.getId(), 0, -1);
    }

    @DeleteMapping("/history")
    public void deleteHistory(Authentication authentication) {
        if (isAnonymous(authentication)) return;

        userRepository.findByEmail(authentication.getName()).ifPresent(user ->
                redisTemplate.delete("search:history:" + user.getId())
        );
    }
}
