package com.example.atm.service;

import com.example.atm.entity.CryptoKey;
import com.example.atm.repository.CryptoKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for managing cryptographic keys with rotation support.
 * Handles key lifecycle: ACTIVE → PENDING → EXPIRED.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CryptoKeyService {

    private final CryptoKeyRepository cryptoKeyRepository;

    /**
     * Get the currently active key for a terminal and key type.
     * This is the primary method used by participants for crypto operations.
     *
     * @param terminalId Terminal identifier (e.g., "TRM-ISS001-ATM-001")
     * @param keyType Key type (TPK or TSK)
     * @return Active key
     * @throws RuntimeException if no active key found
     */
    @Transactional(readOnly = true)
    public CryptoKey getActiveKey(String terminalId, CryptoKey.KeyType keyType) {
        log.debug("Fetching active {} key for terminal: {}", keyType, terminalId);

        return cryptoKeyRepository.findActiveKey(terminalId, keyType)
                .orElseThrow(() -> new RuntimeException(
                        String.format("No active %s key found for terminal: %s", keyType, terminalId)));
    }

    /**
     * Get all valid keys (ACTIVE + PENDING) for a terminal and key type.
     * Used during transition periods when we need to try multiple keys.
     *
     * @param terminalId Terminal identifier
     * @param keyType Key type (TPK or TSK)
     * @return List of valid keys, ordered by version (newest first)
     */
    @Transactional(readOnly = true)
    public List<CryptoKey> getValidKeys(String terminalId, CryptoKey.KeyType keyType) {
        log.debug("Fetching valid {} keys for terminal: {}", keyType, terminalId);
        return cryptoKeyRepository.findValidKeysForTerminal(terminalId, keyType);
    }

    /**
     * Get a specific key version.
     * Used for fallback or audit purposes.
     *
     * @param terminalId Terminal identifier
     * @param keyType Key type
     * @param version Key version number
     * @return Key if found
     */
    @Transactional(readOnly = true)
    public CryptoKey getKeyByVersion(String terminalId, CryptoKey.KeyType keyType, Integer version) {
        return cryptoKeyRepository.findByTerminalIdAndKeyTypeAndKeyVersion(terminalId, keyType, version)
                .orElseThrow(() -> new RuntimeException(
                        String.format("Key not found: terminal=%s, type=%s, version=%d",
                                terminalId, keyType, version)));
    }

    /**
     * Prepare for key rotation by adding a new PENDING key.
     * The new key will be used alongside the ACTIVE key during transition.
     *
     * @param terminalId Terminal identifier
     * @param bankUuid Bank UUID for key derivation context
     * @param keyType Key type to rotate
     * @param newKeyValue New key value in hex format (64 chars)
     * @param rotationId HSM rotation ID (UUID from HSM)
     * @return The newly created PENDING key
     */
    @Transactional
    public CryptoKey addPendingKey(String terminalId, String bankUuid,
                                    CryptoKey.KeyType keyType, String newKeyValue, String rotationId) {
        log.info("Adding PENDING {} key for terminal: {}, rotationId: {}", keyType, terminalId, rotationId);

        // Get next version number
        Integer maxVersion = cryptoKeyRepository.findMaxVersion(terminalId, keyType);
        Integer nextVersion = maxVersion + 1;

        // Create new PENDING key
        CryptoKey newKey = new CryptoKey();
        newKey.setTerminalId(terminalId);
        newKey.setBankUuid(bankUuid);
        newKey.setKeyType(keyType);
        newKey.setKeyValue(newKeyValue);
        newKey.setRotationId(rotationId);
        newKey.setStatus(CryptoKey.KeyStatus.PENDING);
        newKey.setKeyVersion(nextVersion);
        newKey.setEffectiveFrom(LocalDateTime.now());

        CryptoKey savedKey = cryptoKeyRepository.save(newKey);
        log.info("Created PENDING {} key version {} for terminal: {}, rotationId: {}",
                keyType, nextVersion, terminalId, rotationId);

        return savedKey;
    }

    /**
     * Activate a PENDING key and expire the current ACTIVE key.
     * This completes the key rotation process.
     *
     * @param terminalId Terminal identifier
     * @param keyType Key type
     * @param newVersion Version number to activate
     */
    @Transactional
    public void activateKey(String terminalId, CryptoKey.KeyType keyType, Integer newVersion) {
        log.info("Activating {} key version {} for terminal: {}",
                keyType, newVersion, terminalId);

        // Find the PENDING key to activate
        CryptoKey pendingKey = cryptoKeyRepository
                .findByTerminalIdAndKeyTypeAndKeyVersion(terminalId, keyType, newVersion)
                .orElseThrow(() -> new RuntimeException(
                        String.format("PENDING key not found: terminal=%s, type=%s, version=%d",
                                terminalId, keyType, newVersion)));

        if (pendingKey.getStatus() != CryptoKey.KeyStatus.PENDING) {
            throw new RuntimeException(
                    String.format("Key is not PENDING: terminal=%s, type=%s, version=%d, status=%s",
                            terminalId, keyType, newVersion, pendingKey.getStatus()));
        }

        // Expire current ACTIVE key
        cryptoKeyRepository.findActiveKey(terminalId, keyType).ifPresent(activeKey -> {
            activeKey.setStatus(CryptoKey.KeyStatus.EXPIRED);
            activeKey.setEffectiveUntil(LocalDateTime.now());
            cryptoKeyRepository.save(activeKey);
            log.info("Expired previous ACTIVE {} key version {} for terminal: {}",
                    keyType, activeKey.getKeyVersion(), terminalId);
        });

        // Activate the PENDING key
        pendingKey.setStatus(CryptoKey.KeyStatus.ACTIVE);
        pendingKey.setEffectiveFrom(LocalDateTime.now());
        cryptoKeyRepository.save(pendingKey);

        log.info("Successfully activated {} key version {} for terminal: {}",
                keyType, newVersion, terminalId);
    }

    /**
     * Remove a PENDING key after installation failure.
     * Used when terminal reports key installation failure.
     *
     * @param terminalId Terminal identifier
     * @param keyType Key type
     */
    @Transactional
    public void removePendingKey(String terminalId, CryptoKey.KeyType keyType) {
        log.info("Removing PENDING {} key for terminal: {}", keyType, terminalId);

        // Find the PENDING key
        CryptoKey pendingKey = getPendingKey(terminalId, keyType);
        if (pendingKey != null) {
            cryptoKeyRepository.delete(pendingKey);
            log.info("Removed PENDING {} key version {} for terminal: {}",
                    keyType, pendingKey.getKeyVersion(), terminalId);
        } else {
            log.warn("No PENDING {} key found to remove for terminal: {}", keyType, terminalId);
        }
    }

    /**
     * Get the PENDING key for a terminal and key type.
     *
     * @param terminalId Terminal identifier
     * @param keyType Key type
     * @return PENDING key if found, null otherwise
     */
    @Transactional(readOnly = true)
    public CryptoKey getPendingKey(String terminalId, CryptoKey.KeyType keyType) {
        List<CryptoKey> validKeys = getValidKeys(terminalId, keyType);
        return validKeys.stream()
                .filter(key -> key.getStatus() == CryptoKey.KeyStatus.PENDING)
                .findFirst()
                .orElse(null);
    }

    /**
     * Complete key rotation: add PENDING key and immediately activate it.
     * This is a convenience method that combines addPendingKey + activateKey.
     *
     * @param terminalId Terminal identifier
     * @param bankUuid Bank UUID
     * @param keyType Key type to rotate
     * @param newKeyValue New key value in hex format
     * @param rotationId HSM rotation ID (UUID from HSM)
     * @return The newly activated key
     */
    @Transactional
    public CryptoKey rotateKey(String terminalId, String bankUuid,
                               CryptoKey.KeyType keyType, String newKeyValue, String rotationId) {
        log.info("Rotating {} key for terminal: {}", keyType, terminalId);

        // Add PENDING key
        CryptoKey pendingKey = addPendingKey(terminalId, bankUuid, keyType, newKeyValue, rotationId);

        // Immediately activate it
        activateKey(terminalId, keyType, pendingKey.getKeyVersion());

        return cryptoKeyRepository.findActiveKey(terminalId, keyType)
                .orElseThrow(() -> new RuntimeException("Failed to retrieve activated key"));
    }

    /**
     * Get all valid keys for a terminal (all types).
     * Useful for diagnostics and monitoring.
     */
    @Transactional(readOnly = true)
    public List<CryptoKey> getAllValidKeysForTerminal(String terminalId) {
        return cryptoKeyRepository.findAllValidKeysForTerminal(terminalId);
    }
}
