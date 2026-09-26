package com.example.vap_back.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "analysis_job", uniqueConstraints = @UniqueConstraint(name = "uk_analysis_job_job_id", columnNames = "job_id"))
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false, length = 36)
    private String jobId;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 500)
    private String symbols;

    @Column(nullable = false)
    private Integer days;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "llm_narrative", columnDefinition = "TEXT")
    private String llmNarrative;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void markProcessing() {
        status = "PROCESSING";
    }

    public void markCompleted(String narrative) {
        status = "COMPLETED";
        llmNarrative = narrative;
        errorMessage = null;
    }

    public void markFailed(String message) {
        status = "FAILED";
        errorMessage = message;
    }

    public void recordRecoveryAttempt(String newEventId) {
        this.eventId = newEventId;
        this.retryCount += 1;
        this.lastAttemptAt = LocalDateTime.now();
        this.status = "PENDING";
    }
}
