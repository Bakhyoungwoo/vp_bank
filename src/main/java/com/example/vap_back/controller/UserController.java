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
    @GetMapping
    public List<User> list() {
        return userService.findAll();
    }
}