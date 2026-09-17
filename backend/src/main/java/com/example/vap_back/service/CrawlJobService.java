package com.example.vap_back.service;

import com.example.vap_back.dto.CrawlJob;

public interface CrawlJobService {
    CrawlJob create(String jobId, String category);
    CrawlJob get(String jobId);
    void markProcessing(String jobId);
    void markCompleted(String jobId);
    void markFailed(String jobId, String message);
}
