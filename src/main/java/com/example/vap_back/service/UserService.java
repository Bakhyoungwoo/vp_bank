package com.example.vap_back.service;

import com.example.vap_back.Entity.User; // 소문자 entity로 통일
import com.example.vap_back.config.JwtTokenProvider;
import com.example.vap_back.dto.UserEvent;
import com.example.vap_back.dto.UserRequest;
import com.example.vap_back.kafka.UserProducer;
import com.example.vap_back.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository repository;
    private final UserProducer producer;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 회원가입: 비밀번호를 암호화하여 저장하고 Kafka 이벤트를 발행합니다.
     */
    @Transactional
    public User signup(UserRequest request) {
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .department(request.getDepartment())
                .interestCategory(request.getInterestCategory())
                .build();

        User saved = repository.save(user);

        // Kafka로 가입 이벤트 전송
        UserEvent event = new UserEvent(saved.getId(), "CREATED");
        producer.send(event);

        return saved;
    }

    /**
     * 로그인: 이메일과 비밀번호를 검증하고 JWT 토큰을 발급합니다.
     */
    public String login(String email, String password) {
        User user = repository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 사용자입니다."));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("비밀번호가 일치하지 않습니다.");
        }

        // 실제 JWT 토큰 생성 및 반환
        return jwtTokenProvider.createToken(user.getEmail());
    }

    public List<User> findAll() {
        return repository.findAll();
    }
}