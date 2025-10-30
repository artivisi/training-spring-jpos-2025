package com.example.atm.service;

import com.example.atm.dto.rotation.KeyRotationConfirmation;
import com.example.atm.dto.rotation.KeyRotationRequest;
import com.example.atm.dto.rotation.KeyRotationResponse;
import com.example.atm.entity.CryptoKey;
import com.example.atm.util.CryptoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for terminal-initiated key rotation.
 * Handles the complete rotation workflow:
 * 1. Request rotation from HSM
 * 2. Decrypt and verify new key
 * 3. Install new key as PENDING
 * 4. Test new key (optional)
 * 5. Confirm to HSM
 * 6. Activate new key
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KeyRotationService {

    private final CryptoKeyService cryptoKeyService;
    private final HsmClient hsmClient;

    /**
     * Initiate key rotation for a terminal.
     * This is a terminal-initiated (SCHEDULED) rotation.
     *
     * @param terminalId Terminal identifier
     * @param keyType Key type to rotate (TPK or TSK)
     * @param gracePeriodHours Hours before old key expires
     * @param description Optional description for audit
     * @return Rotation ID for tracking
     */
    @Transactional
    public String initiateKeyRotation(String terminalId, CryptoKey.KeyType keyType,
                                     Integer gracePeriodHours, String description) {
        log.info("Initiating {} key rotation for terminal: {}", keyType, terminalId);

        // Step 1: Request rotation from HSM
        KeyRotationResponse rotationResponse = requestRotationFromHsm(
                terminalId, keyType.name(), gracePeriodHours, description);

        log.info("Received rotation response: rotationId={}, status={}",
                rotationResponse.getRotationId(), rotationResponse.getRotationStatus());

        // Step 2: Get current active key
        CryptoKey currentKey = cryptoKeyService.getActiveKey(terminalId, keyType);
        byte[] currentMasterKeyBytes = CryptoUtil.hexToBytes(currentKey.getKeyValue());

        // Step 3: Decrypt new key
        byte[] decryptedNewKey = CryptoUtil.decryptRotationKey(
                rotationResponse.getEncryptedNewKey(), currentMasterKeyBytes);

        // Step 4: Verify checksum
        boolean checksumValid = CryptoUtil.verifyKeyChecksum(
                decryptedNewKey, rotationResponse.getNewKeyChecksum());

        if (!checksumValid) {
            log.error("Key checksum verification failed! Aborting rotation: {}",
                     rotationResponse.getRotationId());
            throw new RuntimeException("Key checksum mismatch - rotation aborted for security");
        }

        // Step 5: Store new key as PENDING
        String newKeyHex = CryptoUtil.bytesToHex(decryptedNewKey);
        CryptoKey pendingKey = cryptoKeyService.addPendingKey(
                terminalId, currentKey.getBankUuid(), keyType, newKeyHex);

        log.info("Stored new {} key as PENDING: version={}, rotationId={}",
                keyType, pendingKey.getKeyVersion(), rotationResponse.getRotationId());

        // Step 6: Test new key (optional - implement per key type)
        boolean testPassed = testNewKey(terminalId, keyType, decryptedNewKey);

        if (!testPassed) {
            log.error("New key test failed! Keeping old key active.");
            throw new RuntimeException("New key test failed - rotation aborted");
        }

        // Step 7: Confirm to HSM
        confirmRotationToHsm(terminalId, rotationResponse.getRotationId());

        // Step 8: Activate new key in database
        cryptoKeyService.activateKey(terminalId, keyType, pendingKey.getKeyVersion());

        log.info("Successfully completed key rotation: rotationId={}, newVersion={}",
                rotationResponse.getRotationId(), pendingKey.getKeyVersion());

        return rotationResponse.getRotationId();
    }

    /**
     * Request key rotation from HSM.
     */
    private KeyRotationResponse requestRotationFromHsm(String terminalId, String keyType,
                                                       Integer gracePeriodHours, String description) {
        KeyRotationRequest request = KeyRotationRequest.builder()
                .keyType(keyType)
                .rotationType("SCHEDULED")
                .gracePeriodHours(gracePeriodHours)
                .description(description)
                .build();

        log.debug("Requesting rotation from HSM: terminalId={}, request={}", terminalId, request);

        try {
            KeyRotationResponse response = hsmClient.requestKeyRotation(terminalId, request);
            if (response == null) {
                throw new RuntimeException("HSM returned null response for rotation request");
            }
            return response;
        } catch (Exception e) {
            log.error("Failed to request rotation from HSM: {}", e.getMessage(), e);
            throw new RuntimeException("HSM rotation request failed", e);
        }
    }

    /**
     * Confirm successful key installation to HSM.
     */
    private void confirmRotationToHsm(String terminalId, String rotationId) {
        KeyRotationConfirmation confirmation = KeyRotationConfirmation.builder()
                .rotationId(rotationId)
                .confirmedBy("ATM_SERVER_v1.0")
                .build();

        log.debug("Confirming rotation to HSM: terminalId={}, rotationId={}", terminalId, rotationId);

        try {
            hsmClient.confirmKeyRotation(terminalId, confirmation);
            log.info("Successfully confirmed rotation to HSM: {}", rotationId);
        } catch (Exception e) {
            log.error("Failed to confirm rotation to HSM: {}", e.getMessage(), e);
            throw new RuntimeException("HSM confirmation failed", e);
        }
    }

    /**
     * Test the new key before activating it.
     * This is a placeholder - implement specific tests per key type.
     *
     * @return true if tests pass, false otherwise
     */
    private boolean testNewKey(String terminalId, CryptoKey.KeyType keyType, byte[] newKeyBytes) {
        log.info("Testing new {} key for terminal: {}", keyType, terminalId);

        // TODO: Implement key-specific tests
        // For TPK: Try encrypting a test PIN block
        // For TSK: Try generating a test MAC

        // For now, just log and return true
        log.info("Key test passed (placeholder implementation)");
        return true;
    }
}
