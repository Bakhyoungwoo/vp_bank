package com.example.vap_back.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CrawlJob {
    private String jobId;
    private String category;
    private String status;
    private String message;
    private long createdAt;
    private long updatedAt;
}
