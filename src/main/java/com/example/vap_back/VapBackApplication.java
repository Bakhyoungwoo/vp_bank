package com.example.vap_back;

import com.example.vap_back.service.NewsTestService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class VapBackApplication {

    public static void main(String[] args) {
        SpringApplication.run(VapBackApplication.class, args);
    }

    @Bean
    CommandLineRunner testNewsSave(NewsTestService service) {
        return args -> {
            service.saveTestNews();
        };
    }
}
