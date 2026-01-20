package com.example.vap_back.Entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "employee")
@Getter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String department;
    private String position;
    private int salary;
    private String status;

    @Column(name = "created_at", updatable = false, insertable = false)
    private LocalDateTime createdAt;
}
