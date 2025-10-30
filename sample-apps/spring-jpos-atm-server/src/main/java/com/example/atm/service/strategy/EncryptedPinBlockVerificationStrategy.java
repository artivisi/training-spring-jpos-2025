package com.example.atm.service.strategy;

import com.example.atm.config.HsmProperties;
import com.example.atm.dto.hsm.PinBlockVerificationRequest;
import com.example.atm.dto.hsm.PinBlockVerificationResponse;
import com.example.atm.entity.Account;
import com.example.atm.entity.PinVerificationType;
import com.example.atm.service.HsmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PIN verification strategy using encrypted PIN block translation method.
 * Compares TPK-encrypted PIN from terminal with LMK-encrypted PIN from database.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EncryptedPinBlockVerificationStrategy implements PinVerificationStrategy {

    private final HsmClient hsmClient;
    private final HsmProperties hsmProperties;

    @Override
    public boolean verify(String pinBlockFromTerminal, String pan, Account account) {
        log.info("Verifying PIN using encrypted PIN block translation for account: {}", account.getAccountNumber());

        String storedPinBlock = account.getEncryptedPinBlock();
        if (storedPinBlock == null || storedPinBlock.isEmpty()) {
            log.error("No encrypted PIN block stored for account: {}", account.getAccountNumber());
            throw new RuntimeException("No encrypted PIN block stored for account");
        }

        try {
            PinBlockVerificationRequest request = PinBlockVerificationRequest.builder()
                    .pinBlockUnderTPK(pinBlockFromTerminal)
                    .pinBlockUnderLMK(storedPinBlock)
                    .terminalId(hsmProperties.getPin().getTerminalId())
                    .pan(pan)
                    .pinFormat(hsmProperties.getPin().getFormat())
                    .encryptionAlgorithm(hsmProperties.getPin().getEncryptionAlgorithm())
                    .build();

            PinBlockVerificationResponse response = hsmClient.verifyPinBlock(request);

            if (response.isValid()) {
                log.info("PIN verification successful for account: {}. Algorithm: {}, TPK: {}, LMK: {}",
                        account.getAccountNumber(), response.getEncryptionAlgorithm(),
                        response.getTpkKeyId(), response.getLmkKeyId());
                return true;
            } else {
                log.warn("PIN verification failed for account: {}. Message: {}",
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
        return PinVerificationType.ENCRYPTED_PIN_BLOCK;
    }
}
