package com.example.vap_back.service.impl;

import com.example.vap_back.Entity.Bookmark;
import com.example.vap_back.Entity.News;
import com.example.vap_back.dto.BookmarkRequestDto;
import com.example.vap_back.exception.UserNotFoundException;
import com.example.vap_back.repository.BookmarkRepository;
import com.example.vap_back.repository.UserRepository;
import com.example.vap_back.repository.NewsRepository;
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
    private final NewsRepository newsRepository;

    @Transactional
    @Override
    public String toggleBookmark(String email, BookmarkRequestDto dto) {
        Long userId = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email))
                .getId();
        News news = newsRepository.findByUrl(dto.getUrl())
                .orElseThrow(() -> new IllegalArgumentException("북마크할 뉴스를 찾을 수 없습니다: " + dto.getUrl()));

        return bookmarkRepository.findByUserIdAndNewsId(userId, news.getId())
                .map(bookmark -> {
                    bookmarkRepository.delete(bookmark);
                    return "스크랩 취소됨";
                })
                .orElseGet(() -> {
                    bookmarkRepository.save(Bookmark.builder()
                            .userId(userId)
                            .news(news)
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
