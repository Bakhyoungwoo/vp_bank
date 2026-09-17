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

    public NewsCrawlEvent(String category, long timestamp) {
        this(null, category, timestamp);
    }
}
