package com.artivisi.atm.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing cryptographic keys for terminal operations.
 * Supports key rotation with multiple versions and lifecycle states.
 */
@Entity
@Table(name = "crypto_keys")
@Data
public class CryptoKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "key_type", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private KeyType keyType;

    @Column(name = "terminal_id", nullable = false, length = 50)
    private String terminalId;

    @Column(name = "bank_uuid", nullable = false, length = 50)
    private String bankUuid;

    @Column(name = "key_value", nullable = false, length = 128)
    private String keyValue;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private KeyStatus status;

    @Column(name = "key_version", nullable = false)
    private Integer keyVersion;

    @Column(name = "rotation_id", length = 100)
    private String rotationId;

    @Column(name = "effective_from", nullable = false)
    private LocalDateTime effectiveFrom;

    @Column(name = "effective_until")
    private LocalDateTime effectiveUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum KeyType {
        /** Terminal PIN Key - used for PIN block encryption/decryption */
        TPK,
        /** Terminal Security Key - used for MAC generation/verification */
        TSK
    }

    public enum KeyStatus {
        /** Currently active and in use */
        ACTIVE,
        /** Scheduled for activation, used during transition period */
        PENDING,
        /** No longer valid, kept for audit trail */
        EXPIRED
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (effectiveFrom == null) {
            effectiveFrom = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
