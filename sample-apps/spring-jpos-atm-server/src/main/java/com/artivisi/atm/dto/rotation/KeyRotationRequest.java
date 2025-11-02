package com.artivisi.atm.dto.rotation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for terminal-initiated key rotation.
 * Sent to HSM to request a new key.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KeyRotationRequest {

    /**
     * Type of key to rotate: TPK or TSK.
     */
    private String keyType;

    /**
     * Type of rotation: SCHEDULED for terminal-initiated.
     */
    private String rotationType;

    /**
     * Optional description for audit trail.
     */
    private String description;

    /**
     * Grace period in hours before old key expires.
     * During grace period, both old and new keys are valid.
     */
    private Integer gracePeriodHours;
}
