package com.example.vap_back.service.impl;

import com.example.vap_back.Entity.AnalysisJob;
import com.example.vap_back.Entity.User;
import com.example.vap_back.Entity.Watchlist;
import com.example.vap_back.dto.AnalysisRequestedEvent;
import com.example.vap_back.exception.UserNotFoundException;
import com.example.vap_back.kafka.AnalysisProducer;
import com.example.vap_back.repository.AnalysisJobRepository;
import com.example.vap_back.repository.UserRepository;
import com.example.vap_back.repository.WatchlistRepository;
import com.example.vap_back.service.AsyncBriefingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AsyncBriefingServiceImpl implements AsyncBriefingService {

    private final UserRepository userRepository;
    private final WatchlistRepository watchlistRepository;
    private final AnalysisJobRepository analysisJobRepository;
    private final AnalysisProducer analysisProducer;

    @Transactional
    @Override
    public AnalysisJob request(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));
        List<Watchlist> watchlist = watchlistRepository.findAllByUserIdOrderByAddedAtDesc(user.getId());
        if (watchlist.isEmpty()) {
            throw new IllegalArgumentException("관심 종목이 없습니다.");
        }

        String jobId = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();
        String symbols = watchlist.stream().map(Watchlist::getSymbol).collect(Collectors.joining(","));
        AnalysisJob job = analysisJobRepository.save(AnalysisJob.builder()
                .jobId(jobId)
                .eventId(eventId)
                .userId(user.getId())
                .symbols(symbols)
                .days(7)
                .status("PENDING")
                .build());

        analysisProducer.publish(new AnalysisRequestedEvent(
                eventId, jobId, user.getId(), symbols, 7, System.currentTimeMillis()));
        return job;
    }

    @Override
    @Transactional(readOnly = true)
    public AnalysisJob get(String email, String jobId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));
        return analysisJobRepository.findByJobId(jobId)
                .filter(job -> job.getUserId().equals(user.getId()))
                .orElseThrow(() -> new IllegalArgumentException("분석 작업을 찾을 수 없습니다."));
    }
}
