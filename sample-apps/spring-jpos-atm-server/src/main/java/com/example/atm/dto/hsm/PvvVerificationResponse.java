package com.example.atm.dto.hsm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for PVV (PIN Verification Value) method verification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PvvVerificationResponse {

    /**
     * Whether the PIN verification was successful.
     */
    private boolean valid;

    /**
     * Descriptive message about verification result.
     */
    private String message;

    /**
     * Verification method used.
     */
    private String method;

    /**
     * Echo of terminal identifier.
     */
    private String terminalId;

    /**
     * Echo of account number.
     */
    private String pan;

    /**
     * Format used for verification.
     */
    private String pinFormat;

    /**
     * TPK key identifier used by HSM.
     */
    private String tpkKeyId;

    /**
     * PVK (PIN Verification Key) identifier used by HSM for PVV calculation.
     */
    private String pvkKeyId;

    /**
     * Echo of stored PVV.
     */
    private String storedPVV;
}
