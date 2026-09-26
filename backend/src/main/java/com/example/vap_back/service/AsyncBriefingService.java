package com.example.vap_back.service;

import com.example.vap_back.Entity.AnalysisJob;

public interface AsyncBriefingService {
    AnalysisJob request(String email);
    AnalysisJob get(String email, String jobId);
}
