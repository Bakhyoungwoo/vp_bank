package com.example.vap_back.controller;

import com.example.vap_back.Entity.User;
import com.example.vap_back.dto.ChangePasswordRequest;
import com.example.vap_back.dto.UserRequest;
import com.example.vap_back.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import java.time.Duration;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/signup")
    public User signup(@Valid @RequestBody UserRequest request) {
        log.info("[SIGNUP] 요청 수신 - email={}", request.getEmail());
        User saved = userService.signup(request);
        log.info("[SIGNUP] 완료 - userId={}, email={}", saved.getId(), saved.getEmail());
        return saved;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@Valid @RequestBody UserRequest request, HttpServletResponse response) {
        log.info("[LOGIN] 요청 수신 - email={}", request.getEmail());
        String token = userService.login(request.getEmail(), request.getPassword());

        ResponseCookie cookie = ResponseCookie.from("access_token", token)
                .httpOnly(true)
                .path("/")
                .maxAge(Duration.ofDays(7))
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        log.info("[LOGIN] 완료 - email={}", request.getEmail());
        return ResponseEntity.ok(Map.of("token", token));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("access_token", "")
                .httpOnly(true)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        log.info("[LOGOUT] 쿠키 삭제 완료");
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication) {
        log.info("[CHANGE_PASSWORD] 요청 수신 - email={}", authentication.getName());
        userService.changePassword(authentication.getName(), request.getCurrentPassword(), request.getNewPassword());
        log.info("[CHANGE_PASSWORD] 완료 - email={}", authentication.getName());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> getMe(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        User user = userService.getUserByEmail(authentication.getName());
        return ResponseEntity.ok(Map.of(
                "email", user.getEmail(),
                "nickname", user.getName() != null ? user.getName() : user.getEmail()
        ));
    }

}