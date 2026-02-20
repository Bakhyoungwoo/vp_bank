package com.example.vap_back.service;

import com.example.vap_back.Entity.User;
import com.example.vap_back.dto.UserRequest;

public interface UserService {
    User signup(UserRequest request);
    String login(String email, String password);
    User getUserByEmail(String email);
    void changePassword(String email, String currentPassword, String newPassword);
}
