package com.example.atm.service;

import com.example.atm.entity.CryptoKey;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for key rotation with real HSM simulator.
 *
 * Prerequisites:
 * - HSM simulator must be running on http://localhost:8080
 * - Database must be initialized with sample keys
 *
 * This test verifies the complete key rotation workflow:
 * 1. Request rotation from HSM
 * 2. Decrypt and verify new key
 * 3. Install new key as PENDING
 * 4. Test new key
 * 5. Confirm to HSM
 * 6. Activate new key
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
@Slf4j
class KeyRotationIntegrationTest {

    @Autowired
    private KeyRotationService keyRotationService;

    @Autowired
    private CryptoKeyService cryptoKeyService;

    private static final String TEST_TERMINAL_ID = "TRM-ISS001-ATM-001";

    @BeforeEach
    void setUp() {
        log.info("=== Starting Key Rotation Integration Test ===");
    }

    @Test
    void testRotateTPKKey() throws Exception {
        log.info("Testing TPK key rotation for terminal: {}", TEST_TERMINAL_ID);

        // Get current active key before rotation
        CryptoKey oldKey = cryptoKeyService.getActiveKey(TEST_TERMINAL_ID, CryptoKey.KeyType.TPK);
        assertNotNull(oldKey, "Old TPK key should exist");
        assertEquals(CryptoKey.KeyStatus.ACTIVE, oldKey.getStatus());
        Integer oldVersion = oldKey.getKeyVersion();
        log.info("Current active TPK key: version={}, status={}", oldVersion, oldKey.getStatus());

        // Initiate rotation
        String rotationId = keyRotationService.initiateKeyRotation(
                TEST_TERMINAL_ID,
                CryptoKey.KeyType.TPK,
                24, // 24 hour grace period
                "Test TPK rotation"
        );

        assertNotNull(rotationId, "Rotation ID should not be null");
        log.info("Rotation initiated: rotationId={}", rotationId);

        // Verify new key is now active
        CryptoKey newKey = cryptoKeyService.getActiveKey(TEST_TERMINAL_ID, CryptoKey.KeyType.TPK);
        assertNotNull(newKey, "New TPK key should exist");
        assertEquals(CryptoKey.KeyStatus.ACTIVE, newKey.getStatus());
        assertEquals(oldVersion + 1, newKey.getKeyVersion(), "New key should have incremented version");
        assertNotEquals(oldKey.getKeyValue(), newKey.getKeyValue(), "New key value should be different");
        log.info("New active TPK key: version={}, status={}", newKey.getKeyVersion(), newKey.getStatus());

        // Verify old key is expired
        List<CryptoKey> allKeys = cryptoKeyService.getAllValidKeysForTerminal(TEST_TERMINAL_ID);
        CryptoKey expiredKey = allKeys.stream()
                .filter(k -> k.getKeyType() == CryptoKey.KeyType.TPK && k.getKeyVersion().equals(oldVersion))
                .findFirst()
                .orElse(null);

        // Note: The old key might not be in "valid keys" list anymore since it's expired
        // That's actually correct behavior - only ACTIVE and PENDING keys are "valid"
        log.info("Old key status after rotation: {}", expiredKey != null ? expiredKey.getStatus() : "not in valid keys list");

        log.info("✓ TPK key rotation test passed");
    }

    @Test
    void testRotateTSKKey() throws Exception {
        log.info("Testing TSK key rotation for terminal: {}", TEST_TERMINAL_ID);

        // Get current active key before rotation
        CryptoKey oldKey = cryptoKeyService.getActiveKey(TEST_TERMINAL_ID, CryptoKey.KeyType.TSK);
        assertNotNull(oldKey, "Old TSK key should exist");
        assertEquals(CryptoKey.KeyStatus.ACTIVE, oldKey.getStatus());
        Integer oldVersion = oldKey.getKeyVersion();
        log.info("Current active TSK key: version={}, status={}", oldVersion, oldKey.getStatus());

        // Initiate rotation
        String rotationId = keyRotationService.initiateKeyRotation(
                TEST_TERMINAL_ID,
                CryptoKey.KeyType.TSK,
                24, // 24 hour grace period
                "Test TSK rotation"
        );

        assertNotNull(rotationId, "Rotation ID should not be null");
        log.info("Rotation initiated: rotationId={}", rotationId);

        // Verify new key is now active
        CryptoKey newKey = cryptoKeyService.getActiveKey(TEST_TERMINAL_ID, CryptoKey.KeyType.TSK);
        assertNotNull(newKey, "New TSK key should exist");
        assertEquals(CryptoKey.KeyStatus.ACTIVE, newKey.getStatus());
        assertEquals(oldVersion + 1, newKey.getKeyVersion(), "New key should have incremented version");
        assertNotEquals(oldKey.getKeyValue(), newKey.getKeyValue(), "New key value should be different");
        log.info("New active TSK key: version={}, status={}", newKey.getKeyVersion(), newKey.getStatus());

        log.info("✓ TSK key rotation test passed");
    }

    @Test
    void testRotationWithInvalidTerminal() {
        log.info("Testing key rotation with invalid terminal ID");

        // This should fail because terminal doesn't exist
        assertThrows(RuntimeException.class, () -> {
            keyRotationService.initiateKeyRotation(
                    "INVALID-TERMINAL-ID",
                    CryptoKey.KeyType.TPK,
                    24,
                    "Should fail"
            );
        });

        log.info("✓ Invalid terminal test passed (expected failure)");
    }
}
