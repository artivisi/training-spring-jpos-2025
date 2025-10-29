package com.example.atm.service.strategy;

import com.example.atm.config.HsmProperties;
import com.example.atm.dto.hsm.PvvVerificationRequest;
import com.example.atm.dto.hsm.PvvVerificationResponse;
import com.example.atm.entity.Account;
import com.example.atm.entity.PinVerificationType;
import com.example.atm.service.HsmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PIN verification strategy using PVV (PIN Verification Value) method.
 * Uses one-way hash to generate PVV from PIN and compares with stored PVV.
 * This is the most common method in banking systems (ISO 9564 compliant).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PvvVerificationStrategy implements PinVerificationStrategy {

    private final HsmClient hsmClient;
    private final HsmProperties hsmProperties;

    @Override
    public boolean verify(String pinBlockFromTerminal, String pan, Account account) {
        log.info("Verifying PIN using PVV method for account: {}", account.getAccountNumber());

        String storedPvv = account.getPvv();
        if (storedPvv == null || storedPvv.isEmpty()) {
            log.error("No PVV stored for account: {}", account.getAccountNumber());
            throw new RuntimeException("No PVV stored for account");
        }

        try {
            PvvVerificationRequest request = PvvVerificationRequest.builder()
                    .pinBlockUnderTPK(pinBlockFromTerminal)
                    .storedPVV(storedPvv)
                    .terminalId(hsmProperties.getPin().getTerminalId())
                    .pan(pan)
                    .pinFormat(hsmProperties.getPin().getFormat())
                    .build();

            PvvVerificationResponse response = hsmClient.verifyWithPvv(request);

            if (response.isValid()) {
                log.info("PVV verification successful for account: {}. TPK: {}, PVK: {}",
                        account.getAccountNumber(), response.getTpkKeyId(), response.getPvkKeyId());
                return true;
            } else {
                log.warn("PVV verification failed for account: {}. Message: {}",
                        account.getAccountNumber(), response.getMessage());
                return false;
            }
        } catch (Exception e) {
            log.error("Error communicating with HSM for account: {}. Error: {}",
                    account.getAccountNumber(), e.getMessage(), e);
            throw new RuntimeException("HSM communication error", e);
        }
    }

    @Override
    public PinVerificationType getType() {
        return PinVerificationType.PVV;
    }
}
