package com.example.vap_back.service.impl;

import com.example.vap_back.dto.CrawlJob;
import com.example.vap_back.service.CrawlJobService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlJobServiceImpl implements CrawlJobService {
    private static final String KEY_PREFIX = "crawl:job:";
    private static final Duration JOB_TTL = Duration.ofHours(1);
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public CrawlJob create(String jobId, String category) {
        long now = System.currentTimeMillis();
        CrawlJob job = new CrawlJob(jobId, category, "QUEUED", null, now, now);
        save(job);
        return job;
    }

    @Override
    public CrawlJob get(String jobId) {
        String raw = redisTemplate.opsForValue().get(KEY_PREFIX + jobId);
        if (raw == null) return null;
        try { return objectMapper.readValue(raw, CrawlJob.class); }
        catch (JsonProcessingException e) { log.warn("Invalid crawl job data: {}", jobId, e); return null; }
    }

    @Override public void markProcessing(String jobId) { update(jobId, "PROCESSING", null); }
    @Override public void markCompleted(String jobId) { update(jobId, "COMPLETED", null); }
    @Override public void markFailed(String jobId, String message) { update(jobId, "FAILED", message); }

    private void update(String jobId, String status, String message) {
        CrawlJob current = get(jobId);
        if (current != null) save(new CrawlJob(current.getJobId(), current.getCategory(), status, message,
                current.getCreatedAt(), System.currentTimeMillis()));
    }

    private void save(CrawlJob job) {
        try { redisTemplate.opsForValue().set(KEY_PREFIX + job.getJobId(), objectMapper.writeValueAsString(job), JOB_TTL); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Could not save crawl job", e); }
    }
}
