package com.artivisi.atm.dto.rotation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO from HSM for key rotation request.
 * Contains the encrypted new key and metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KeyRotationResponse {

    /**
     * Unique identifier for this rotation operation.
     */
    private String rotationId;

    /**
     * Type of key being rotated: TPK or TSK.
     */
    private String keyType;

    /**
     * New key encrypted under current master key (hex format).
     * Format: IV (16 bytes) || ciphertext
     * Encrypted using AES-128-CBC with PKCS5 padding.
     */
    private String encryptedNewKey;

    /**
     * SHA-256 checksum of the decrypted new key (first 16 hex chars).
     * Used to verify key integrity after decryption.
     */
    private String newKeyChecksum;

    /**
     * Deadline for completing key installation.
     * After this time, the old key will be revoked.
     */
    private LocalDateTime gracePeriodEndsAt;

    /**
     * Current status of rotation: IN_PROGRESS, COMPLETED, FAILED.
     */
    private String rotationStatus;
}
