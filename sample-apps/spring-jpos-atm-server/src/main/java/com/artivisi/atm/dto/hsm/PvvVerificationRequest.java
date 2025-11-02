package com.artivisi.atm.dto.hsm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for PVV (PIN Verification Value) method verification.
 * Method B uses one-way hash to generate PVV from PIN.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PvvVerificationRequest {

    /**
     * PIN block from terminal, encrypted under TPK.
     * Hex-encoded string from ISO-8583 field 52.
     */
    private String pinBlockUnderTPK;

    /**
     * Stored PVV from database (4 digits).
     * Generated from original PIN using one-way hash.
     */
    private String storedPVV;

    /**
     * Terminal identifier.
     * HSM uses this to lookup the appropriate TPK for decryption.
     */
    private String terminalId;

    /**
     * Primary Account Number from ISO-8583 field 2.
     * Required for PIN block formats like ISO-0.
     */
    private String pan;

    /**
     * PIN block format (ISO-0, ISO-1, ISO-3, or ISO-4).
     * Defaults to ISO-0 if not specified.
     */
    private PinFormat pinFormat;
}
