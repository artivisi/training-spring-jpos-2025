package com.artivisi.atm.dto.rotation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO to confirm successful key installation to HSM.
 * Sent after terminal has decrypted, verified, installed, and tested the new key.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KeyRotationConfirmation {

    /**
     * Rotation ID received from the initial rotation request.
     */
    private String rotationId;

    /**
     * Identifier of the application confirming the rotation.
     * For audit trail purposes.
     */
    private String confirmedBy;
}
