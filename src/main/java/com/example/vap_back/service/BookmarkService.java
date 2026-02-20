package com.example.vap_back.service;

import com.example.vap_back.Entity.Bookmark;
import com.example.vap_back.dto.BookmarkRequestDto;

import java.util.List;

public interface BookmarkService {
    String toggleBookmark(String email, BookmarkRequestDto dto);
    List<Bookmark> getMyBookmarks(String email);
}
