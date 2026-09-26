package com.example.vap_back.controller;

import com.example.vap_back.Entity.AnalysisJob;
import com.example.vap_back.service.AsyncBriefingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ai/briefing/jobs")
@RequiredArgsConstructor
public class AsyncBriefingController {

    private final AsyncBriefingService asyncBriefingService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> request(Authentication authentication) {
        AnalysisJob job = asyncBriefingService.request(authentication.getName());
        return ResponseEntity.accepted().body(Map.of(
                "jobId", job.getJobId(),
                "eventId", job.getEventId(),
                "status", job.getStatus()));
    }

    @GetMapping("/{jobId}")
    public AnalysisJob get(@PathVariable String jobId, Authentication authentication) {
        return asyncBriefingService.get(authentication.getName(), jobId);
    }
}
