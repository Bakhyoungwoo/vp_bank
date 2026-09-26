package com.example.vap_back;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@EnableScheduling
public class VapBackApplication {

    public static void main(String[] args) {
        SpringApplication.run(VapBackApplication.class, args);
    }
}
