package com.example.atm.service;

import com.example.atm.domain.model.CryptoKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Runtime key manager that holds active cryptographic keys in memory.
 * Keys are loaded from database on startup and updated when rotated.
 *
 * Provides thread-safe access to TPK, TSK, and TMK keys for
 * transaction processing without database lookups.
 */
@Service
@Slf4j
public class RuntimeKeyManager {

    private final CryptoKeyService cryptoKeyService;

    @Value("${crypto.tmk.key}")
    private String tmkFromConfig;

    @Value("${crypto.tpk.key:}")
    private String tpkFallback;

    @Value("${crypto.tsk.key:}")
    private String tskFallback;

    @Value("${crypto.bank.uuid}")
    private String bankUuid;

    // Thread-safe key storage
    private final Map<CryptoKey.KeyType, String> activeKeys = new ConcurrentHashMap<>();

    public RuntimeKeyManager(CryptoKeyService cryptoKeyService) {
        this.cryptoKeyService = cryptoKeyService;
    }

    /**
     * Load active keys from database on application startup.
     * Falls back to application.yml if database keys not found.
     */
    @PostConstruct
    public void loadKeysFromDatabase() {
        log.info("Loading cryptographic keys from database...");

        // TMK is always from configuration (long-lived, securely provisioned)
        activeKeys.put(CryptoKey.KeyType.TMK, tmkFromConfig);
        log.info("Loaded TMK from configuration");

        // Load TPK from database, fallback to config
        try {
            CryptoKey tpkKey = cryptoKeyService.getActiveKey(CryptoKey.KeyType.TPK);
            activeKeys.put(CryptoKey.KeyType.TPK, tpkKey.getKeyValue());
            log.info("Loaded TPK from database: keyId={}, KCV={}",
                    tpkKey.getId(), tpkKey.getCheckValue());
        } catch (Exception e) {
            log.warn("No active TPK in database, using configuration fallback");
            if (tpkFallback != null && !tpkFallback.isEmpty()) {
                activeKeys.put(CryptoKey.KeyType.TPK, tpkFallback);
            }
        }

        // Load TSK from database, fallback to config
        try {
            CryptoKey tskKey = cryptoKeyService.getActiveKey(CryptoKey.KeyType.TSK);
            activeKeys.put(CryptoKey.KeyType.TSK, tskKey.getKeyValue());
            log.info("Loaded TSK from database: keyId={}, KCV={}",
                    tskKey.getId(), tskKey.getCheckValue());
        } catch (Exception e) {
            log.warn("No active TSK in database, using configuration fallback");
            if (tskFallback != null && !tskFallback.isEmpty()) {
                activeKeys.put(CryptoKey.KeyType.TSK, tskFallback);
            }
        }

        log.info("Key loading completed. Active keys: {}", activeKeys.keySet());
    }

    /**
     * Reload a specific key after rotation.
     * Called by KeyChangeService after successful key change.
     */
    public void reloadKey(CryptoKey.KeyType keyType) {
        log.info("Reloading key from database: {}", keyType);

        try {
            CryptoKey key = cryptoKeyService.getActiveKey(keyType);
            activeKeys.put(keyType, key.getKeyValue());
            log.info("Key reloaded successfully: keyType={}, keyId={}, KCV={}",
                    keyType, key.getId(), key.getCheckValue());
        } catch (Exception e) {
            log.error("Failed to reload key: {}", keyType, e);
            throw new RuntimeException("Failed to reload key after rotation", e);
        }
    }

    /**
     * Get active TPK (Terminal PIN Key) for PIN encryption operations.
     */
    public String getTpkKey() {
        String key = activeKeys.get(CryptoKey.KeyType.TPK);
        if (key == null || key.isEmpty()) {
            throw new RuntimeException("TPK not loaded");
        }
        return key;
    }

    /**
     * Get active TSK (Terminal Session Key) for MAC operations.
     */
    public String getTskKey() {
        String key = activeKeys.get(CryptoKey.KeyType.TSK);
        if (key == null || key.isEmpty()) {
            throw new RuntimeException("TSK not loaded");
        }
        return key;
    }

    /**
     * Get TMK (Terminal Master Key) for key decryption operations.
     */
    public String getTmkKey() {
        String key = activeKeys.get(CryptoKey.KeyType.TMK);
        if (key == null || key.isEmpty()) {
            throw new RuntimeException("TMK not configured");
        }
        return key;
    }

    /**
     * Get bank UUID for key derivation.
     */
    public String getBankUuid() {
        return bankUuid;
    }
}
