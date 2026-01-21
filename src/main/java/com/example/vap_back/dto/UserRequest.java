package com.example.vap_back.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter // 필수
@Setter
@NoArgsConstructor
public class UserRequest {
    private String email;
    private String password;
    private String name;
    private String department;
    private String interestCategory;
}