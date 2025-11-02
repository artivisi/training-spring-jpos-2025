package com.artivisi.atm.util;

import com.artivisi.atm.dto.hsm.PinFormat;
import com.artivisi.atm.entity.PinEncryptionAlgorithm;

import lombok.extern.slf4j.Slf4j;

/**
 * Helper class for generating PIN blocks in tests.
 * Supports AES-128 and AES-256 encryption only.
 * This is a simplified implementation for testing purposes.
 * In production, PIN blocks should be generated using proper HSM operations.
 */
@Slf4j
public class PinBlockGenerator {

    /**
     * Generates a PIN block encrypted under TPK using specified algorithm.
     *
     * @param clearPin Clear PIN (4-12 digits)
     * @param pan Primary Account Number
     * @param tpkHex TPK key in hex format (64 hex chars = 32 bytes for AES-256)
     * @param bankUuid Bank UUID for key derivation
     * @param algorithm Encryption algorithm (AES_128 or AES_256)
     * @param format PIN block format (ISO_0, ISO_1, ISO_3, ISO_4)
     * @return Hex-encoded encrypted PIN block (32 bytes = 64 hex chars)
     */
    public static String generatePinBlock(String clearPin, String pan, String tpkHex, String bankUuid,
                                          PinEncryptionAlgorithm algorithm, PinFormat format) {
        try {
            // AES uses ISO 9564 PIN blocks
            byte[] clearPinBlock = AesPinBlockUtil.buildClearPinBlock(clearPin, pan, format);
            byte[] tpkBytes = CryptoUtil.hexToBytes(tpkHex);
            byte[] encrypted = AesPinBlockUtil.encryptPinBlock(clearPinBlock, tpkBytes, bankUuid);
            return CryptoUtil.bytesToHex(encrypted);
        } catch (Exception e) {
            log.error("Error generating PIN block: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate PIN block", e);
        }
    }
}
