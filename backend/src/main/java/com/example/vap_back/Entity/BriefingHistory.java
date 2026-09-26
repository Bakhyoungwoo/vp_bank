package com.example.vap_back.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "briefing_history", uniqueConstraints = {
        @UniqueConstraint(name = "uk_briefing_history_job_id", columnNames = "job_id")
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BriefingHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 누구를 위한 브리핑인지
    @Column(nullable = false)
    private Long userId;

    @Column(name = "job_id", length = 36)
    private String jobId;

    @Column(nullable = false)
    private LocalDateTime generatedAt;

    // 브리핑에 사용된 관심종목 (콤마 구분)
    @Column(nullable = false, length = 500)
    private String symbols;

    private String status;

    @Column(columnDefinition = "TEXT")
    private String llmNarrative;
}
