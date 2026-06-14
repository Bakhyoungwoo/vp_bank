package com.example.vap_back.kafka;

import com.example.vap_back.dto.NewsCrawlEvent;
import com.example.vap_back.service.CrawlLockService;
import com.example.vap_back.service.NewsCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class NewsCrawlConsumerTest {

    @Mock NewsCacheService newsCacheService;
    @Mock CrawlLockService crawlLockService;

    @InjectMocks
    NewsCrawlConsumer consumer;

    @Test
    @DisplayName("크롤링 성공 - lock/unlock 정상 수행 및 crawlAndSave 호출")
    void consume_success_locksAndUnlocks() {
        // given
        NewsCrawlEvent event = new NewsCrawlEvent("it", System.currentTimeMillis());
        given(crawlLockService.isLocked("it")).willReturn(false);

        // when
        consumer.consume(event);

        // then
        then(crawlLockService).should().lock("it");
        then(crawlLockService).should().unlock("it");
        then(newsCacheService).should().crawlAndSave(eq("it"), anyList());
    }

    @Test
    @DisplayName("이미 잠금 상태 - 즉시 리턴, lock·crawlAndSave 미호출")
    void consume_alreadyLocked_skipsProcessing() {
        // given
        NewsCrawlEvent event = new NewsCrawlEvent("it", System.currentTimeMillis());
        given(crawlLockService.isLocked("it")).willReturn(true);

        // when
        consumer.consume(event);

        // then
        then(crawlLockService).should(never()).lock(any());
        then(newsCacheService).shouldHaveNoInteractions();
        then(crawlLockService).should(never()).unlock(any());
    }

    @Test
    @DisplayName("크롤링 실패 - 예외 re-throw로 DLQ 핸들러 트리거, finally에서 unlock 보장")
    void consume_failure_rethrowsExceptionAndUnlocks() {
        // given
        NewsCrawlEvent event = new NewsCrawlEvent("it", System.currentTimeMillis());
        given(crawlLockService.isLocked("it")).willReturn(false);
        willThrow(new RuntimeException("crawl failed"))
                .given(newsCacheService).crawlAndSave(anyString(), anyList());

        // when / then: DefaultErrorHandler가 재시도 후 DLQ 전송할 수 있도록 예외가 전파되어야 함
        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("crawl failed");

        // 예외 발생 후에도 finally 블록에서 unlock이 반드시 호출되어야 함
        then(crawlLockService).should().unlock("it");
    }

    @Test
    @DisplayName("크롤링 실패 시 lock은 수행되었지만 unlock도 수행됨 (락 해제 보장)")
    void consume_failure_lockWasAcquiredThenReleased() {
        // given
        NewsCrawlEvent event = new NewsCrawlEvent("economy", System.currentTimeMillis());
        given(crawlLockService.isLocked("economy")).willReturn(false);
        willThrow(new IllegalStateException("Redis down"))
                .given(newsCacheService).crawlAndSave(anyString(), anyList());

        // when
        assertThatThrownBy(() -> consumer.consume(event))
                .isInstanceOf(IllegalStateException.class);

        // then: lock 획득 후 실패해도 unlock이 호출됨
        then(crawlLockService).should().lock("economy");
        then(crawlLockService).should().unlock("economy");
    }
}
