package com.example.vap_back.kafka;

import com.example.vap_back.Entity.User;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class UserConsumer {

    @KafkaListener(topics = "employee-topic", groupId = "user-group")
    public void consume(User user) {

        System.out.println("======================================");
        System.out.println("📥 [Kafka] User Created Event Received");
        System.out.println("ID        : " + user.getId());
        System.out.println("Name      : " + user.getName());
        System.out.println("Department: " + user.getDepartment());
        System.out.println("Position  : " + user.getPosition());
        System.out.println("======================================");
    }
}
