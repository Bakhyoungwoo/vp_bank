package com.example.vap_back.controller;

import com.example.vap_back.Entity.Transaction;
import com.example.vap_back.Entity.User;
import com.example.vap_back.repository.UserRepository;
import com.example.vap_back.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final UserRepository userRepository;

    @PostMapping("/{userId}")
    public ResponseEntity<String> createTestTransaction(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> requestData) {

        // 1. 테스트를 위해 기존 User 확인
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        // 2. 요청 데이터를 기반으로 Transaction 객체 생성 (빌더 패턴 사용)
        Transaction transaction = Transaction.builder()
                .user(user)
                .amount(Long.parseLong(requestData.get("amount").toString()))
                .merchant(requestData.get("merchant").toString())
                .category(requestData.get("category").toString())
                .latitude(Double.parseDouble(requestData.get("latitude").toString()))
                .longitude(Double.parseDouble(requestData.get("longitude").toString()))
                .build();

        // 3. 서비스 호출 (DB 저장 + Kafka 발행)
        transactionService.createTransaction(transaction);

        return ResponseEntity.ok("거래 데이터가 성공적으로 생성되었으며 Kafka로 전송되었습니다.");
    }
}