package com.example.vap_back.controller;

import com.example.vap_back.Entity.Bookmark;
import com.example.vap_back.config.JwtTokenProvider;
import com.example.vap_back.config.SecurityConfig;
import com.example.vap_back.dto.BookmarkRequestDto;
import com.example.vap_back.service.BookmarkService;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BookmarkController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class BookmarkControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean BookmarkService bookmarkService;
    @MockBean JwtTokenProvider jwtTokenProvider;

    // ── toggleBookmark ────────────────────────────────────────────────────────

    @Test
    @DisplayName("북마크 추가 - 인증된 사용자, 200")
    @WithMockUser(username = "test@test.com")
    void toggleBookmark_add() throws Exception {
        BookmarkRequestDto dto = new BookmarkRequestDto();
        dto.setUrl("http://news.example.com/1");
        dto.setTitle("테스트 뉴스");
        dto.setPress("테스트 언론");
        dto.setTime("2024-01-01");

        given(bookmarkService.toggleBookmark(eq("test@test.com"), any())).willReturn("추가됨");

        mockMvc.perform(post("/api/bookmarks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(content().string("추가됨"));

        then(bookmarkService).should().toggleBookmark(eq("test@test.com"), any());
    }

    @Test
    @DisplayName("북마크 제거 - 이미 있으면 제거 후 메시지 반환")
    @WithMockUser(username = "test@test.com")
    void toggleBookmark_remove() throws Exception {
        BookmarkRequestDto dto = new BookmarkRequestDto();
        dto.setUrl("http://news.example.com/1");
        dto.setTitle("테스트 뉴스");

        given(bookmarkService.toggleBookmark(eq("test@test.com"), any())).willReturn("제거됨");

        mockMvc.perform(post("/api/bookmarks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(content().string("제거됨"));
    }

    @Test
    @DisplayName("북마크 토글 실패 - 미인증 시 401")
    void toggleBookmark_unauthenticated_401() throws Exception {
        BookmarkRequestDto dto = new BookmarkRequestDto();
        dto.setUrl("http://news.example.com/1");

        // csrf()로 CSRF 필터를 통과시켜 실제 인증 체크(401)가 동작하도록 함
        mockMvc.perform(post("/api/bookmarks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized());
    }

    // ── getMyBookmarks ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("북마크 목록 조회 - 인증된 사용자, 목록 반환")
    @WithMockUser(username = "test@test.com")
    void getMyBookmarks_success() throws Exception {
        Bookmark bookmark = Bookmark.builder()
                .id(1L).userId(1L)
                .newsUrl("http://news.example.com/1")
                .title("테스트 뉴스")
                .press("테스트 언론")
                .savedAt(LocalDateTime.of(2024, 1, 1, 12, 0))
                .build();
        given(bookmarkService.getMyBookmarks("test@test.com")).willReturn(List.of(bookmark));

        mockMvc.perform(get("/api/bookmarks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].newsUrl").value("http://news.example.com/1"))
                .andExpect(jsonPath("$[0].title").value("테스트 뉴스"));
    }

    @Test
    @DisplayName("북마크 목록 조회 - 북마크 없을 때 빈 배열 반환")
    @WithMockUser(username = "test@test.com")
    void getMyBookmarks_empty() throws Exception {
        given(bookmarkService.getMyBookmarks("test@test.com")).willReturn(List.of());

        mockMvc.perform(get("/api/bookmarks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("북마크 목록 조회 실패 - 미인증 시 401")
    void getMyBookmarks_unauthenticated_401() throws Exception {
        mockMvc.perform(get("/api/bookmarks"))
                .andExpect(status().isUnauthorized());
    }
}
