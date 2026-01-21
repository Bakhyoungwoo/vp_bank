package com.example.vap_back.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private Long amount;
    private String merchant;
    private String category;
    private double latitude;
    private double longitude;

    @CreatedDate
    @Column(name = "transaction_at", updatable = false)
    private LocalDateTime transactionAt;

    private boolean isFraud;

    public void updateFraudStatus(boolean isFraud) {
        this.isFraud = isFraud;
    }
}