package com.example.vap_back.service;

import com.example.vap_back.Entity.User;
import com.example.vap_back.config.JwtTokenProvider;
import com.example.vap_back.dto.UserEvent;
import com.example.vap_back.dto.UserRequest;
import com.example.vap_back.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository repository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final ApplicationEventPublisher eventPublisher; // 이벤트 발행자 주입

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

        // 직접 호출 대신 이벤트 발행. DB 커밋 성공 시 Producer가 작동함.
        eventPublisher.publishEvent(new UserEvent(saved.getId(), saved.getEmail(), "CREATED"));
        return saved;
    }

    @Transactional
    public String login(String email, String password) {
        User user = repository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 사용자입니다."));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            eventPublisher.publishEvent(new UserEvent(user.getId(), email, "LOGIN_FAIL"));
            throw new RuntimeException("비밀번호가 일치하지 않습니다.");
        }

        eventPublisher.publishEvent(new UserEvent(user.getId(), email, "LOGIN_SUCCESS"));
        return jwtTokenProvider.createToken(user.getEmail());
    }

    public List<User> findAll() {
        return repository.findAll();
    }
}