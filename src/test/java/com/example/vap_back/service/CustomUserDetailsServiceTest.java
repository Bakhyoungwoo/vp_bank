package com.example.vap_back.service;

import com.example.vap_back.Entity.User;
import com.example.vap_back.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock UserRepository userRepository;

    @InjectMocks
    CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("이메일로 사용자 로드 성공 - email/password 일치하는 UserDetails 반환")
    void loadUserByUsername_found_returnsUserDetails() {
        // given
        User user = User.builder().id(1L).email("user@test.com").password("encoded-pw").build();
        given(userRepository.findByEmail("user@test.com")).willReturn(Optional.of(user));

        // when
        UserDetails result = customUserDetailsService.loadUserByUsername("user@test.com");

        // then
        assertThat(result.getUsername()).isEqualTo("user@test.com");
        assertThat(result.getPassword()).isEqualTo("encoded-pw");
        assertThat(result.getAuthorities()).isEmpty();
    }

    @Test
    @DisplayName("이메일로 사용자 로드 실패 - 없는 이메일이면 UsernameNotFoundException")
    void loadUserByUsername_notFound_throwsException() {
        // given
        given(userRepository.findByEmail("none@test.com")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("none@test.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("none@test.com");
    }
}
