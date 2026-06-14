package com.example.vap_back.service;

import com.example.vap_back.Entity.Bookmark;
import com.example.vap_back.Entity.User;
import com.example.vap_back.dto.BookmarkRequestDto;
import com.example.vap_back.exception.UserNotFoundException;
import com.example.vap_back.repository.BookmarkRepository;
import com.example.vap_back.repository.UserRepository;
import com.example.vap_back.service.impl.BookmarkServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class BookmarkServiceTest {

    @Mock BookmarkRepository bookmarkRepository;
    @Mock UserRepository userRepository;

    @InjectMocks
    BookmarkServiceImpl bookmarkService;

    private User testUser() {
        return User.builder().id(1L).email("user@test.com").password("enc").build();
    }

    private BookmarkRequestDto testDto(String url) {
        BookmarkRequestDto dto = new BookmarkRequestDto();
        dto.setUrl(url);
        dto.setTitle("테스트 기사");
        dto.setPress("연합뉴스");
        dto.setTime("2024-01-01T00:00:00");
        return dto;
    }

    @Test
    @DisplayName("북마크 추가 - 새 북마크면 저장 후 '스크랩 저장됨' 반환")
    void toggleBookmark_save() {
        // given
        given(userRepository.findByEmail("user@test.com")).willReturn(Optional.of(testUser()));
        given(bookmarkRepository.findByUserIdAndNewsUrl(1L, "http://news.com")).willReturn(Optional.empty());

        // when
        String result = bookmarkService.toggleBookmark("user@test.com", testDto("http://news.com"));

        // then
        assertThat(result).isEqualTo("스크랩 저장됨");
        then(bookmarkRepository).should().save(any(Bookmark.class));
    }

    @Test
    @DisplayName("북마크 취소 - 이미 존재하면 삭제 후 '스크랩 취소됨' 반환")
    void toggleBookmark_delete() {
        // given
        Bookmark existing = Bookmark.builder()
                .id(10L).userId(1L).newsUrl("http://news.com").savedAt(LocalDateTime.now()).build();
        given(userRepository.findByEmail("user@test.com")).willReturn(Optional.of(testUser()));
        given(bookmarkRepository.findByUserIdAndNewsUrl(1L, "http://news.com")).willReturn(Optional.of(existing));

        // when
        String result = bookmarkService.toggleBookmark("user@test.com", testDto("http://news.com"));

        // then
        assertThat(result).isEqualTo("스크랩 취소됨");
        then(bookmarkRepository).should().delete(existing);
    }

    @Test
    @DisplayName("북마크 추가 실패 - 없는 사용자면 UserNotFoundException")
    void toggleBookmark_userNotFound() {
        // given
        given(userRepository.findByEmail("none@test.com")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> bookmarkService.toggleBookmark("none@test.com", testDto("http://news.com")))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("내 북마크 조회 성공 - savedAt 내림차순 목록 반환")
    void getMyBookmarks_success() {
        // given
        Bookmark b1 = Bookmark.builder().id(1L).userId(1L).newsUrl("http://news1.com")
                .savedAt(LocalDateTime.now()).build();
        Bookmark b2 = Bookmark.builder().id(2L).userId(1L).newsUrl("http://news2.com")
                .savedAt(LocalDateTime.now().minusDays(1)).build();
        given(userRepository.findByEmail("user@test.com")).willReturn(Optional.of(testUser()));
        given(bookmarkRepository.findAllByUserIdOrderBySavedAtDesc(1L)).willReturn(List.of(b1, b2));

        // when
        List<Bookmark> result = bookmarkService.getMyBookmarks("user@test.com");

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getNewsUrl()).isEqualTo("http://news1.com");
    }

    @Test
    @DisplayName("내 북마크 조회 실패 - 없는 사용자면 UserNotFoundException")
    void getMyBookmarks_userNotFound() {
        // given
        given(userRepository.findByEmail("none@test.com")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> bookmarkService.getMyBookmarks("none@test.com"))
                .isInstanceOf(UserNotFoundException.class);
    }
}
