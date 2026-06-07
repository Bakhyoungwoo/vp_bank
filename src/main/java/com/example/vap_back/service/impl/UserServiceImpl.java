package com.example.vap_back.service.impl;

import com.example.vap_back.Entity.User;
import com.example.vap_back.config.JwtTokenProvider;
import com.example.vap_back.dto.UserEvent;
import com.example.vap_back.dto.UserRequest;
import com.example.vap_back.exception.InvalidCredentialsException;
import com.example.vap_back.exception.UserNotFoundException;
import com.example.vap_back.repository.UserRepository;
import com.example.vap_back.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository repository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    @Override
    public User signup(UserRequest request) {
        log.debug("[UserService.signup] 시작 - email={}", request.getEmail());

        if (repository.findByEmail(request.getEmail()).isPresent()) {
            log.warn("[UserService.signup] 이미 존재하는 이메일 - email={}", request.getEmail());
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다: " + request.getEmail());
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .department(request.getDepartment())
                .interestCategory(request.getInterestCategory())
                .build();

        User saved = repository.save(user);
        log.debug("[UserService.signup] DB 저장 완료 - userId={}", saved.getId());

        eventPublisher.publishEvent(new UserEvent(saved.getId(), saved.getEmail(), "CREATED"));
        log.debug("[UserService.signup] 이벤트 발행 완료 - CREATED");
        return saved;
    }

    @Transactional
    @Override
    public String login(String email, String password) {
        log.debug("[UserService.login] 시작 - email={}", email);

        User user = repository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            log.warn("[UserService.login] 비밀번호 불일치 - email={}", email);
            eventPublisher.publishEvent(new UserEvent(user.getId(), email, "LOGIN_FAIL"));
            throw new InvalidCredentialsException();
        }

        eventPublisher.publishEvent(new UserEvent(user.getId(), email, "LOGIN_SUCCESS"));
        log.debug("[UserService.login] 완료 - email={}", email);
        return jwtTokenProvider.createToken(user.getEmail());
    }

    @Override
    public User getUserByEmail(String email) {
        return repository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));
    }

    @Transactional
    @Override
    public void changePassword(String email, String currentPassword, String newPassword) {
        log.debug("[UserServiceImpl.changePassword] 시작 - email={}", email);

        User user = repository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            log.warn("[UserServiceImpl.changePassword] 현재 비밀번호 불일치 - email={}", email);
            throw new InvalidCredentialsException();
        }

        user.updatePassword(passwordEncoder.encode(newPassword));
        log.debug("[UserServiceImpl.changePassword] 완료 - email={}", email);
    }
}
