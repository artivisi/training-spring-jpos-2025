package com.artivisi.atm.domain.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "crypto_keys", indexes = {
    @Index(name = "idx_crypto_keys_type_status", columnList = "key_type, status"),
    @Index(name = "idx_crypto_keys_expires_at", columnList = "expires_at")
})
@Data
public class CryptoKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "key_type", length = 10, nullable = false)
    private KeyType keyType;

    @Column(name = "key_value", length = 500, nullable = false)
    private String keyValue;

    @Column(name = "check_value", length = 50)
    private String checkValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private KeyStatus status = KeyStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    public enum KeyType {
        TMK,
        TPK,
        TSK
    }

    public enum KeyStatus {
        ACTIVE,
        EXPIRED,
        REVOKED
    }
}
