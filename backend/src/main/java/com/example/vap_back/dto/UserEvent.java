package com.example.vap_back.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserEvent {
    private Long userId;
    private String email;
    private String action; // "CREATED", "LOGIN_SUCCESS", "LOGIN_FAIL"
}