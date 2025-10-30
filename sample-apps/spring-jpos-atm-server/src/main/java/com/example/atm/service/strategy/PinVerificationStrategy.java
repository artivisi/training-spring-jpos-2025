package com.example.atm.service.strategy;

import com.example.atm.entity.Account;
import com.example.atm.entity.PinVerificationType;

/**
 * Strategy interface for different PIN verification methods.
 * Each implementation handles a specific verification type.
 */
public interface PinVerificationStrategy {

    /**
     * Verify PIN against account's stored credentials.
     *
     * @param pinBlockFromTerminal PIN block from terminal, encrypted under TPK (field 52/123)
     * @param pan Primary Account Number (field 2)
     * @param account Account entity containing stored PIN credentials
     * @param terminalId Full terminal ID (field 42 + field 41)
     * @return true if PIN is valid, false otherwise
     */
    boolean verify(String pinBlockFromTerminal, String pan, Account account, String terminalId);

    /**
     * Get the verification type supported by this strategy.
     *
     * @return PinVerificationType enum value
     */
    PinVerificationType getType();
}
