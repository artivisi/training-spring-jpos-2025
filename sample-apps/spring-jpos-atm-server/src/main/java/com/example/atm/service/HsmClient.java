package com.example.atm.service;

import com.example.atm.dto.hsm.PinBlockVerificationRequest;
import com.example.atm.dto.hsm.PinBlockVerificationResponse;
import com.example.atm.dto.hsm.PvvVerificationRequest;
import com.example.atm.dto.hsm.PvvVerificationResponse;
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
    @PostExchange("${hsm.pin.encrypted-pin-block.endpoint}")
    PinBlockVerificationResponse verifyPinBlock(PinBlockVerificationRequest request);

    /**
     * Verify PIN using PVV (PIN Verification Value) method.
     * Uses one-way hash to generate PVV from PIN and compares with stored PVV.
     *
     * Endpoint: /api/hsm/pin/verify-with-pvv
     */
    @PostExchange("${hsm.pin.pvv.endpoint}")
    PvvVerificationResponse verifyWithPvv(PvvVerificationRequest request);
}
