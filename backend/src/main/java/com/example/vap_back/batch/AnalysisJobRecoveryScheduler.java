package com.example.vap_back.batch;

import com.example.vap_back.Entity.AnalysisJob;
import com.example.vap_back.dto.AnalysisRequestedEvent;
import com.example.vap_back.kafka.AnalysisProducer;
import com.example.vap_back.repository.AnalysisJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * AnalysisConsumer가 예외로 죽거나 재시도가 소진돼 메시지가 유실된 경우,
 * PENDING/PROCESSING에 멈춰 있는 job을 찾아 새 eventId로 재발행한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "analysis.worker.enabled", havingValue = "true", matchIfMissing = true)
public class AnalysisJobRecoveryScheduler {

    private static final List<String> STUCK_STATUSES = List.of("PENDING", "PROCESSING");

    private final AnalysisJobRepository analysisJobRepository;
    private final AnalysisProducer analysisProducer;

    @Value("${analysis.recovery.stuck-after-seconds:180}")
    private long stuckAfterSeconds;

    @Value("${analysis.recovery.max-retries:3}")
    private int maxRetries;

    @Scheduled(fixedDelayString = "${analysis.recovery.interval-ms:60000}")
    @Transactional
    public void recoverStuckJobs() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(stuckAfterSeconds);
        List<AnalysisJob> stuckJobs = analysisJobRepository.findByStatusInAndUpdatedAtBefore(STUCK_STATUSES, cutoff);

        for (AnalysisJob job : stuckJobs) {
            if (job.getRetryCount() >= maxRetries) {
                job.markFailed("자동 복구 재시도 횟수 초과");
                log.warn("[ANALYSIS-RECOVERY] jobId={} 재시도 한도 초과, FAILED 처리", job.getJobId());
                continue;
            }

            String newEventId = UUID.randomUUID().toString();
            job.recordRecoveryAttempt(newEventId);
            analysisProducer.publish(new AnalysisRequestedEvent(
                    newEventId, job.getJobId(), job.getUserId(), job.getSymbols(), job.getDays(),
                    System.currentTimeMillis()));
            log.info("[ANALYSIS-RECOVERY] jobId={} 재발행 (retryCount={})", job.getJobId(), job.getRetryCount());
        }
    }
}
