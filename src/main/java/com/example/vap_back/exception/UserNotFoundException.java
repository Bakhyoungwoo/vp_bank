package com.example.vap_back.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String email) {
        super("사용자를 찾을 수 없습니다: " + email);
    }
}