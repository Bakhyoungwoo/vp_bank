package com.example.vap_back.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NewsCrawlEvent {
    private String jobId;
    private String category;
    private long timestamp;
    private String requestId;

    public NewsCrawlEvent(String category, long timestamp) {
        this(null, category, timestamp, null);
    }

    public NewsCrawlEvent(String jobId, String category, long timestamp) {
        this(jobId, category, timestamp, null);
    }
}
