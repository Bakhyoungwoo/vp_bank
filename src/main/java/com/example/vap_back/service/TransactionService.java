package com.example.vap_back.service;

import com.example.vap_back.Entity.Transaction;
import com.example.vap_back.kafka.TransactionProducer; // 1. 명시적으로 import 추가
import com.example.vap_back.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionProducer transactionProducer; // 2. 필드 선언 (에러 해결 핵심)
    private final ObjectMapper objectMapper;

    @Transactional
    public void createTransaction(Transaction transaction) {
        // DB에 거래 정보 저장
        Transaction saved = transactionRepository.save(transaction);

        try {
            // AI 서버 전송용 데이터 구성
            Map<String, Object> data = new HashMap<>();
            data.put("id", saved.getId());
            data.put("amount", saved.getAmount());
            data.put("latitude", saved.getLatitude());
            data.put("longitude", saved.getLongitude());

            // JSON 문자열로 변환
            String jsonMessage = objectMapper.writeValueAsString(data);

            // 3. 만들어둔 Producer의 메서드 호출
            transactionProducer.sendTransaction(jsonMessage);

        } catch (Exception e) {
            System.err.println("Kafka 전송 중 오류 발생: " + e.getMessage());
        }
    }
}