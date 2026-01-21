package com.example.vap_back.controller;

import com.example.vap_back.Entity.User;
import com.example.vap_back.service.UserInterestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interests")
@RequiredArgsConstructor
public class UserInterestController {

    private final UserInterestService userInterestService;

    // 관심사 등록
    @PostMapping
    public ResponseEntity<?> addInterest(
            Authentication authentication,
            @RequestBody Map<String, String> request
    ) {
        String email = authentication.getName();
        User user = userInterestService.getUserByEmail(email);

        userInterestService.addInterest(user, request.get("category"));
        return ResponseEntity.ok("관심사 등록 완료");
    }

    // 내 관심사 조회
    @GetMapping
    public ResponseEntity<?> getMyInterests(
            Authentication authentication
    ) {
        String email = authentication.getName();
        User user = userInterestService.getUserByEmail(email);

        List<String> interests = userInterestService.getUserCategoryList(user);
        return ResponseEntity.ok(interests);
    }
}
