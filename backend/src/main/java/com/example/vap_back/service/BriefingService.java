package com.example.vap_back.service;

import com.example.vap_back.Entity.BriefingHistory;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

public interface BriefingService {
    ResponseEntity<Map<String, Object>> generateBriefing(String email);
    List<BriefingHistory> getMyBriefingHistory(String email);
}
