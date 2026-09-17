package com.example.vap_back.controller;

import com.example.vap_back.Entity.User;
import com.example.vap_back.config.JwtTokenProvider;
import com.example.vap_back.config.SecurityConfig;
import com.example.vap_back.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SearchController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class SearchControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean StringRedisTemplate redisTemplate;
    @MockBean UserRepository userRepository;
    @MockBean JwtTokenProvider jwtTokenProvider;

    @SuppressWarnings("unchecked")
    private ListOperations<String, String> listOps = mock(ListOperations.class);

    private static final User TEST_USER = User.builder()
            .id(1L).email("test@test.com").password("enc").build();

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForList()).willReturn(listOps);
    }

    // ── saveSearchHistory ─────────────────────────────────────────────────────

    @Test
    @DisplayName("검색어 저장 - 인증된 사용자, Redis에 최대 5건 유지")
    @WithMockUser(username = "test@test.com")
    void saveSearchHistory_authenticated() throws Exception {
        given(userRepository.findByEmail("test@test.com")).willReturn(Optional.of(TEST_USER));

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("java"))
                .andExpect(status().isOk());

        then(listOps).should().remove("search:history:1", 0, "java");
        then(listOps).should().leftPush("search:history:1", "java");
        then(listOps).should().trim("search:history:1", 0, 4);
    }

    @Test
    @DisplayName("검색어 저장 - 비인증 사용자는 저장 없이 즉시 리턴")
    void saveSearchHistory_anonymous_skips() throws Exception {
        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("\"java\""))
                .andExpect(status().isOk());

        then(userRepository).shouldHaveNoInteractions();
        then(listOps).shouldHaveNoInteractions();
    }

    // ── getSearchHistory ──────────────────────────────────────────────────────

    @Test
    @DisplayName("검색 히스토리 조회 - 인증된 사용자, 최근 검색어 목록 반환")
    @WithMockUser(username = "test@test.com")
    void getSearchHistory_authenticated() throws Exception {
        given(userRepository.findByEmail("test@test.com")).willReturn(Optional.of(TEST_USER));
        given(listOps.range("search:history:1", 0, -1)).willReturn(List.of("java", "spring", "redis"));

        mockMvc.perform(get("/api/search/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("java"))
                .andExpect(jsonPath("$[1]").value("spring"))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @DisplayName("검색 히스토리 조회 - 비인증 사용자는 빈 배열 반환")
    void getSearchHistory_anonymous_returnsEmpty() throws Exception {
        mockMvc.perform(get("/api/search/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── deleteHistory ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("검색 히스토리 삭제 - 인증된 사용자, Redis key 삭제")
    @WithMockUser(username = "test@test.com")
    void deleteHistory_authenticated() throws Exception {
        given(userRepository.findByEmail("test@test.com")).willReturn(Optional.of(TEST_USER));

        mockMvc.perform(delete("/api/search/history"))
                .andExpect(status().isOk());

        then(redisTemplate).should().delete("search:history:1");
    }

    @Test
    @DisplayName("검색 히스토리 삭제 - 비인증 사용자는 삭제 없이 즉시 리턴")
    void deleteHistory_anonymous_skips() throws Exception {
        mockMvc.perform(delete("/api/search/history"))
                .andExpect(status().isOk());

        then(redisTemplate).should(never()).delete(anyString());
    }
}
