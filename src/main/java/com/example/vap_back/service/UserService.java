package com.example.vap_back.service;

import com.example.vap_back.Entity.User;
import com.example.vap_back.dto.UserEvent;
import com.example.vap_back.kafka.UserProducer;
import com.example.vap_back.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository repository;
    private final UserProducer producer;

    @Transactional
    public User create(User user) {
        User saved = repository.save(user);

        // 이벤트를 생성하여 전송
        UserEvent event = new UserEvent(saved.getId(), "CREATED");
        producer.send(event);

        return saved;
    }

    public List<User> findAll() {
        return repository.findAll();
    }
}
