package com.example.vap_back.controller;

import com.example.vap_back.Entity.Bookmark;
import com.example.vap_back.service.BookmarkService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookmarks")
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @PostMapping
    public String toggleBookmark(@RequestBody BookmarkRequestDto dto, Authentication authentication) {
        return bookmarkService.toggleBookmark(authentication.getName(), dto);
    }

    @GetMapping
    public List<Bookmark> getMyBookmarks(Authentication authentication) {
        return bookmarkService.getMyBookmarks(authentication.getName());
    }

    @Data
    public static class BookmarkRequestDto {
        private String url;
        private String title;
        private String press;
        private String time;
    }
}
