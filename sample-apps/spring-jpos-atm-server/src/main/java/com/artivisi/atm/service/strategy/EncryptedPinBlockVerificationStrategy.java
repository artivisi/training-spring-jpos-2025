package com.artivisi.atm.service.strategy;

import com.artivisi.atm.config.HsmProperties;
import com.artivisi.atm.dto.hsm.PinBlockVerificationRequest;
import com.artivisi.atm.dto.hsm.PinBlockVerificationResponse;
import com.artivisi.atm.entity.Account;
import com.artivisi.atm.entity.PinVerificationType;
import com.artivisi.atm.service.HsmClient;
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
    public boolean verify(String pinBlockFromTerminal, String pan, Account account, String terminalId) {
        log.info("Verifying PIN using encrypted PIN block translation for account: {} terminal: {}",
                account.getAccountNumber(), terminalId);

        String storedPinBlock = account.getEncryptedPinBlock();
        if (storedPinBlock == null || storedPinBlock.isEmpty()) {
            log.error("No encrypted PIN block stored for account: {}", account.getAccountNumber());
            throw new RuntimeException("No encrypted PIN block stored for account");
        }

        try {
            PinBlockVerificationRequest request = PinBlockVerificationRequest.builder()
                    .pinBlockUnderTPK(pinBlockFromTerminal)
                    .pinBlockUnderLMK(storedPinBlock)
                    .terminalId(terminalId)
                    .pan(pan)
                    .pinFormat(hsmProperties.getPin().getFormat())
                    .encryptionAlgorithm(hsmProperties.getPin().getEncryptionAlgorithm())
                    .build();

            PinBlockVerificationResponse response = hsmClient.verifyPinBlock(request);

            log.info("PIN verification result: {}", response.isValid() ? "VALID" : "INVALID");

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
