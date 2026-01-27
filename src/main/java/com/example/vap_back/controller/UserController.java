package com.example.vap_back.controller;

import com.example.vap_back.Entity.User;
import com.example.vap_back.dto.UserRequest;
import com.example.vap_back.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/signup")
    public User signup(@RequestBody UserRequest request) {
        return userService.signup(request);
    }

    @PostMapping("/login")
    public String login(@RequestBody UserRequest request) {
        return userService.login(request.getEmail(), request.getPassword());
    }
    // 자동 로그인
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body("로그인이 필요합니다.");
        }

        // 인증된 사용자 정보 가져오기
        String email = authentication.getName();
        User user = userService.getUserByEmail(email); // (서비스 이름이 userInterestService일 수도 있음. 확인 필요)

        // 닉네임과 이메일 반환
        return ResponseEntity.ok(Map.of(
                "email", user.getEmail(),
                "nickname", user.getName()
        ));
    }

    @GetMapping
    public List<User> list() {
        return userService.findAll();
    }
}