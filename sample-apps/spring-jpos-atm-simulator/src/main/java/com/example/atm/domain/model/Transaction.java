package com.example.atm.domain.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_transactions_id_accounts", columnList = "id_accounts"),
    @Index(name = "idx_transactions_timestamp", columnList = "timestamp"),
    @Index(name = "idx_transactions_type", columnList = "type")
})
@Data
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "id_accounts", nullable = false)
    private UUID idAccounts;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_accounts", referencedColumnName = "id", insertable = false, updatable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 50, nullable = false)
    private TransactionType type;

    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private TransactionStatus status;

    @CreationTimestamp
    @Column(name = "timestamp", nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @Column(name = "request_msg", columnDefinition = "TEXT")
    private String requestMsg;

    @Column(name = "response_msg", columnDefinition = "TEXT")
    private String responseMsg;

    @Column(name = "response_code", length = 10)
    private String responseCode;

    @Column(name = "terminal_id", length = 50)
    private String terminalId;

    public enum TransactionType {
        BALANCE,
        WITHDRAWAL
    }

    public enum TransactionStatus {
        SUCCESS,
        FAILED
    }
}
