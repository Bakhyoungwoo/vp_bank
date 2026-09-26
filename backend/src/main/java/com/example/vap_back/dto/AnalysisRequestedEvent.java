package com.example.vap_back.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisRequestedEvent {
    private String eventId;
    private String jobId;
    private Long userId;
    private String symbols;
    private Integer days;
    private long requestedAt;
}
