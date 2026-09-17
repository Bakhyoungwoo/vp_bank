package com.example.vap_back.service.impl;

import com.example.vap_back.Entity.Bookmark;
import com.example.vap_back.dto.BookmarkRequestDto;
import com.example.vap_back.exception.UserNotFoundException;
import com.example.vap_back.repository.BookmarkRepository;
import com.example.vap_back.repository.UserRepository;
import com.example.vap_back.service.BookmarkService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookmarkServiceImpl implements BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final UserRepository userRepository;

    @Transactional
    @Override
    public String toggleBookmark(String email, BookmarkRequestDto dto) {
        Long userId = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email))
                .getId();

        return bookmarkRepository.findByUserIdAndNewsUrl(userId, dto.getUrl())
                .map(bookmark -> {
                    bookmarkRepository.delete(bookmark);
                    return "스크랩 취소됨";
                })
                .orElseGet(() -> {
                    bookmarkRepository.save(Bookmark.builder()
                            .userId(userId)
                            .newsUrl(dto.getUrl())
                            .title(dto.getTitle())
                            .press(dto.getPress())
                            .publishedAt(dto.getTime())
                            .savedAt(LocalDateTime.now())
                            .build());
                    return "스크랩 저장됨";
                });
    }

    @Override
    public List<Bookmark> getMyBookmarks(String email) {
        Long userId = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email))
                .getId();
        return bookmarkRepository.findAllByUserIdOrderBySavedAtDesc(userId);
    }
}
