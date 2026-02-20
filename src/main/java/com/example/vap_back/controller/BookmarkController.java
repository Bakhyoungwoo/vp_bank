package com.example.vap_back.controller;

import com.example.vap_back.Entity.Bookmark;
import com.example.vap_back.dto.BookmarkRequestDto;
import com.example.vap_back.service.BookmarkService;
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
}
