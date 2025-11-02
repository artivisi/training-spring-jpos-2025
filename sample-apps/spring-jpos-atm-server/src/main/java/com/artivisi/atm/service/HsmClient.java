package com.artivisi.atm.service;

import com.artivisi.atm.dto.hsm.PinBlockVerificationRequest;
import com.artivisi.atm.dto.hsm.PinBlockVerificationResponse;
import com.artivisi.atm.dto.hsm.PvvVerificationRequest;
import com.artivisi.atm.dto.hsm.PvvVerificationResponse;
import com.artivisi.atm.dto.rotation.KeyRotationConfirmation;
import com.artivisi.atm.dto.rotation.KeyRotationRequest;
import com.artivisi.atm.dto.rotation.KeyRotationResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * HTTP client interface for HSM operations.
 * Uses Spring's HTTP Interface with RestClient backing.
 */
@HttpExchange
public interface HsmClient {

    /**
     * Verify PIN using encrypted PIN block translation method.
     * Compares TPK-encrypted PIN from terminal with LMK-encrypted PIN from database.
     *
     * Endpoint: /api/hsm/pin/verify-with-translation
     */
    @PostExchange("/api/hsm/pin/verify-with-translation")
    PinBlockVerificationResponse verifyPinBlock(@RequestBody PinBlockVerificationRequest request);

    /**
     * Verify PIN using PVV (PIN Verification Value) method.
     * Uses one-way hash to generate PVV from PIN and compares with stored PVV.
     *
     * Endpoint: /api/hsm/pin/verify-with-pvv
     */
    @PostExchange("/api/hsm/pin/verify-with-pvv")
    PvvVerificationResponse verifyWithPvv(@RequestBody PvvVerificationRequest request);

    /**
     * Request key rotation for a terminal.
     * Terminal-initiated (SCHEDULED) rotation where HSM generates new key
     * and encrypts it under current terminal master key.
     *
     * Endpoint: /api/hsm/terminal/{terminalId}/request-rotation
     */
    @PostExchange("/api/hsm/terminal/{terminalId}/request-rotation")
    KeyRotationResponse requestKeyRotation(@PathVariable String terminalId,
                                          @RequestBody KeyRotationRequest request);

    /**
     * Confirm successful key installation to HSM.
     * Called after terminal has decrypted, verified, installed, and tested the new key.
     * HSM will mark rotation as completed and revoke old key.
     *
     * Endpoint: /api/hsm/terminal/{terminalId}/confirm-key-update
     */
    @PostExchange("/api/hsm/terminal/{terminalId}/confirm-key-update")
    void confirmKeyRotation(@PathVariable String terminalId,
                           @RequestBody KeyRotationConfirmation confirmation);
}
