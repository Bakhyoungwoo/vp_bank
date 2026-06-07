package com.example.vap_back.controller;

import com.example.vap_back.Entity.User;
import com.example.vap_back.config.JwtTokenProvider;
import com.example.vap_back.config.SecurityConfig;
import com.example.vap_back.dto.ChangePasswordRequest;
import com.example.vap_back.dto.UserRequest;
import com.example.vap_back.exception.InvalidCredentialsException;
import com.example.vap_back.exception.UserNotFoundException;
import com.example.vap_back.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean UserService userService;
    @MockBean JwtTokenProvider jwtTokenProvider;

    // ── signup ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("회원가입 성공 - 200, 저장된 User 반환")
    void signup_success() throws Exception {
        UserRequest req = new UserRequest();
        req.setEmail("test@test.com");
        req.setPassword("password1234");
        req.setName("홍길동");

        User saved = User.builder().id(1L).email("test@test.com").password("enc").name("홍길동").build();
        given(userService.signup(any())).willReturn(saved);

        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.email").value("test@test.com"));
    }

    @Test
    @DisplayName("회원가입 실패 - 이메일 누락 시 400")
    void signup_missingEmail_400() throws Exception {
        UserRequest req = new UserRequest();
        req.setPassword("password1234");

        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("회원가입 실패 - 비밀번호 8자 미만 시 400")
    void signup_shortPassword_400() throws Exception {
        UserRequest req = new UserRequest();
        req.setEmail("test@test.com");
        req.setPassword("short");

        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("회원가입 실패 - 중복 이메일 시 400")
    void signup_duplicateEmail_400() throws Exception {
        UserRequest req = new UserRequest();
        req.setEmail("dup@test.com");
        req.setPassword("password1234");
        given(userService.signup(any()))
                .willThrow(new IllegalArgumentException("이미 존재하는 이메일: dup@test.com"));

        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("로그인 성공 - 200, JSON body에 token, Set-Cookie 헤더 포함")
    void login_success_tokenAndCookie() throws Exception {
        UserRequest req = new UserRequest();
        req.setEmail("test@test.com");
        req.setPassword("password1234");
        given(userService.login("test@test.com", "password1234")).willReturn("jwt-token");

        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(header().string("Set-Cookie", containsString("access_token")));
    }

    @Test
    @DisplayName("로그인 실패 - 없는 이메일 시 404")
    void login_userNotFound_404() throws Exception {
        UserRequest req = new UserRequest();
        req.setEmail("none@test.com");
        req.setPassword("password1234");
        given(userService.login(anyString(), anyString()))
                .willThrow(new UserNotFoundException("none@test.com"));

        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("로그인 실패 - 비밀번호 불일치 시 401")
    void login_wrongPassword_401() throws Exception {
        UserRequest req = new UserRequest();
        req.setEmail("test@test.com");
        req.setPassword("wrongpassword");
        given(userService.login(anyString(), anyString()))
                .willThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    // ── logout ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("로그아웃 성공 - 200, Set-Cookie Max-Age=0으로 쿠키 만료")
    void logout_clearsCookie() throws Exception {
        mockMvc.perform(post("/api/users/logout"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
    }

    // ── changePassword ────────────────────────────────────────────────────────

    @Test
    @DisplayName("비밀번호 변경 성공 - 인증된 사용자, 200")
    @WithMockUser(username = "test@test.com")
    void changePassword_success() throws Exception {
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword("current1234");
        req.setNewPassword("newpass1234");
        willDoNothing().given(userService).changePassword(anyString(), anyString(), anyString());

        mockMvc.perform(patch("/api/users/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        then(userService).should().changePassword("test@test.com", "current1234", "newpass1234");
    }

    @Test
    @DisplayName("비밀번호 변경 실패 - 미인증 시 401")
    void changePassword_unauthenticated_401() throws Exception {
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword("current1234");
        req.setNewPassword("newpass1234");

        mockMvc.perform(patch("/api/users/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    // ── me ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("내 정보 조회 성공 - email/nickname 반환")
    @WithMockUser(username = "test@test.com")
    void getMe_success() throws Exception {
        User user = User.builder().id(1L).email("test@test.com").name("홍길동").password("enc").build();
        given(userService.getUserByEmail("test@test.com")).willReturn(user);

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@test.com"))
                .andExpect(jsonPath("$.nickname").value("홍길동"));
    }

    @Test
    @DisplayName("내 정보 조회 - 이름 없는 사용자는 nickname을 email로 반환")
    @WithMockUser(username = "noname@test.com")
    void getMe_noName_usesEmailAsNickname() throws Exception {
        User user = User.builder().id(2L).email("noname@test.com").password("enc").build();
        given(userService.getUserByEmail("noname@test.com")).willReturn(user);

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("noname@test.com"));
    }
}
