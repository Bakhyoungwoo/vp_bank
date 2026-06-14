package com.example.vap_back.service;

import com.example.vap_back.Entity.User;
import com.example.vap_back.config.JwtTokenProvider;
import com.example.vap_back.dto.UserEvent;
import com.example.vap_back.dto.UserRequest;
import com.example.vap_back.exception.InvalidCredentialsException;
import com.example.vap_back.exception.UserNotFoundException;
import com.example.vap_back.repository.UserRepository;
import com.example.vap_back.service.impl.UserServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository repository;
    @Mock BCryptPasswordEncoder passwordEncoder;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks
    UserServiceImpl userService;

    // ── signup ────────────────────────────────────────────────

    @Test
    @DisplayName("회원가입 성공 - 저장된 User 반환 및 CREATED 이벤트 발행")
    void signup_success() {
        // given
        UserRequest request = new UserRequest();
        request.setEmail("test@test.com");
        request.setPassword("password1234");
        request.setName("홍길동");

        User savedUser = User.builder()
                .id(1L).email("test@test.com").password("encoded").name("홍길동").build();

        given(repository.findByEmail("test@test.com")).willReturn(Optional.empty());
        given(passwordEncoder.encode("password1234")).willReturn("encoded");
        given(repository.save(any(User.class))).willReturn(savedUser);

        // when
        User result = userService.signup(request);

        // then
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getEmail()).isEqualTo("test@test.com");
        then(eventPublisher).should().publishEvent(argThat((Object e) ->
                e instanceof UserEvent && "CREATED".equals(((UserEvent) e).getAction())));
    }

    @Test
    @DisplayName("회원가입 실패 - 중복 이메일이면 IllegalArgumentException")
    void signup_duplicateEmail_throwsException() {
        // given
        UserRequest request = new UserRequest();
        request.setEmail("dup@test.com");
        request.setPassword("password1234");

        User existing = User.builder().id(1L).email("dup@test.com").password("enc").build();
        given(repository.findByEmail("dup@test.com")).willReturn(Optional.of(existing));

        // when & then
        assertThatThrownBy(() -> userService.signup(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dup@test.com");
    }

    // ── login ─────────────────────────────────────────────────

    @Test
    @DisplayName("로그인 성공 - JWT 토큰 반환 및 LOGIN_SUCCESS 이벤트 발행")
    void login_success() {
        // given
        User user = User.builder().id(1L).email("test@test.com").password("encoded").build();
        given(repository.findByEmail("test@test.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("rawPass", "encoded")).willReturn(true);
        given(jwtTokenProvider.createToken("test@test.com")).willReturn("jwt-token");

        // when
        String token = userService.login("test@test.com", "rawPass");

        // then
        assertThat(token).isEqualTo("jwt-token");
        then(eventPublisher).should().publishEvent(argThat((Object e) ->
                e instanceof UserEvent && "LOGIN_SUCCESS".equals(((UserEvent) e).getAction())));
    }

    @Test
    @DisplayName("로그인 실패 - 존재하지 않는 이메일이면 UserNotFoundException")
    void login_userNotFound_throwsException() {
        // given
        given(repository.findByEmail("none@test.com")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.login("none@test.com", "pass"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("로그인 실패 - 비밀번호 불일치 시 InvalidCredentialsException 및 LOGIN_FAIL 이벤트")
    void login_wrongPassword_throwsException() {
        // given
        User user = User.builder().id(1L).email("test@test.com").password("encoded").build();
        given(repository.findByEmail("test@test.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong", "encoded")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> userService.login("test@test.com", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);
        then(eventPublisher).should().publishEvent(argThat((Object e) ->
                e instanceof UserEvent && "LOGIN_FAIL".equals(((UserEvent) e).getAction())));
    }

    // ── getUserByEmail ────────────────────────────────────────

    @Test
    @DisplayName("이메일로 사용자 조회 성공")
    void getUserByEmail_found() {
        // given
        User user = User.builder().id(1L).email("test@test.com").password("enc").build();
        given(repository.findByEmail("test@test.com")).willReturn(Optional.of(user));

        // when
        User result = userService.getUserByEmail("test@test.com");

        // then
        assertThat(result.getEmail()).isEqualTo("test@test.com");
    }

    @Test
    @DisplayName("이메일로 사용자 조회 실패 - 없는 이메일이면 UserNotFoundException")
    void getUserByEmail_notFound_throwsException() {
        // given
        given(repository.findByEmail("none@test.com")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.getUserByEmail("none@test.com"))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ── changePassword ────────────────────────────────────────

    @Test
    @DisplayName("비밀번호 변경 성공 - 새 비밀번호로 업데이트됨")
    void changePassword_success() {
        // given
        User user = User.builder().id(1L).email("test@test.com").password("encoded_old").build();
        given(repository.findByEmail("test@test.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("currentPass", "encoded_old")).willReturn(true);
        given(passwordEncoder.encode("newPass")).willReturn("encoded_new");

        // when
        userService.changePassword("test@test.com", "currentPass", "newPass");

        // then
        assertThat(user.getPassword()).isEqualTo("encoded_new");
    }

    @Test
    @DisplayName("비밀번호 변경 실패 - 없는 사용자면 UserNotFoundException")
    void changePassword_userNotFound_throwsException() {
        // given
        given(repository.findByEmail("none@test.com")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.changePassword("none@test.com", "cur", "new"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("비밀번호 변경 실패 - 현재 비밀번호 불일치 시 InvalidCredentialsException")
    void changePassword_wrongCurrentPassword_throwsException() {
        // given
        User user = User.builder().id(1L).email("test@test.com").password("encoded_old").build();
        given(repository.findByEmail("test@test.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrongCur", "encoded_old")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> userService.changePassword("test@test.com", "wrongCur", "new"))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
