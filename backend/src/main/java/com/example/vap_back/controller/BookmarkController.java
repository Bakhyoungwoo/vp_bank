package com.example.vap_back.controller;

import com.example.vap_back.Entity.Bookmark;
import com.example.vap_back.dto.BookmarkRequestDto;
import com.example.vap_back.service.BookmarkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookmarks")
@RequiredArgsConstructor
@Tag(name = "Bookmark", description = "북마크 추가/조회")
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @Operation(summary = "북마크 토글", description = "뉴스를 북마크에 추가하거나 이미 있으면 제거합니다.")
    @PostMapping
    public String toggleBookmark(@RequestBody BookmarkRequestDto dto, Authentication authentication) {
        return bookmarkService.toggleBookmark(authentication.getName(), dto);
    }

    @Operation(summary = "내 북마크 조회", description = "인증된 사용자의 북마크 목록을 저장 날짜 내림차순으로 반환합니다.")
    @GetMapping
    public List<Bookmark> getMyBookmarks(Authentication authentication) {
        return bookmarkService.getMyBookmarks(authentication.getName());
    }
}
