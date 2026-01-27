package com.example.vap_back.controller;

import com.example.vap_back.Entity.Bookmark;
import com.example.vap_back.Entity.User;
import com.example.vap_back.repository.BookmarkRepository;
import com.example.vap_back.repository.UserRepository;
import com.example.vap_back.service.UserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/bookmarks")
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkRepository bookmarkRepository;
    private final UserRepository userRepository;

    // 스크랩
    @PostMapping
    public String toggleBookmark(@RequestBody BookmarkRequestDto dto) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));

        // 이미 저장된 뉴스인지 확인
        return bookmarkRepository.findByUserIdAndNewsUrl(user.getId(), dto.getUrl())
                .map(bookmark -> {
                    bookmarkRepository.delete(bookmark);
                    return "스크랩 취소됨";
                })
                .orElseGet(() -> {
                    bookmarkRepository.save(Bookmark.builder()
                            .userId(user.getId())
                            .newsUrl(dto.getUrl())
                            .title(dto.getTitle())
                            .press(dto.getPress())
                            .publishedAt(dto.getTime())
                            .savedAt(LocalDateTime.now())
                            .build());
                    return "스크랩 저장됨";
                });
    }

    // 내 스크랩 목록 조회
    @GetMapping
    public List<Bookmark> getMyBookmarks() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        return bookmarkRepository.findAllByUserIdOrderBySavedAtDesc(user.getId());
    }

    @Data
    public static class BookmarkRequestDto {
        private String url;
        private String title;
        private String press;
        private String time;
    }
}