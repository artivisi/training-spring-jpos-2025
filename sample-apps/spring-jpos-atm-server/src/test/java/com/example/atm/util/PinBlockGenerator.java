package com.example.atm.util;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * Helper class for generating PIN blocks in tests.
 * This is a simplified implementation for testing purposes.
 * In production, PIN blocks should be generated using proper HSM operations.
 */
@Slf4j
public class PinBlockGenerator {

    /**
     * Generates a PIN block (ISO Format 0) encrypted under TPK.
     * Simplified implementation for testing.
     *
     * @param clearPin Clear PIN (4-12 digits)
     * @param pan Primary Account Number (12-19 digits)
     * @param tpkHex TPK key in hex format (16 or 32 hex chars for DES/3DES)
     * @return Hex-encoded encrypted PIN block
     */
    public static String generatePinBlock(String clearPin, String pan, String tpkHex) {
        try {
            // ISO Format 0: 0 + PIN length + PIN + padding + XOR with PAN
            String pinBlock = buildIsoFormat0PinBlock(clearPin, pan);

            // Encrypt PIN block with TPK
            byte[] encrypted = encryptWithTpk(hexToBytes(pinBlock), tpkHex);

            return bytesToHex(encrypted);
        } catch (Exception e) {
            log.error("Error generating PIN block: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate PIN block", e);
        }
    }

    /**
     * Build ISO Format 0 PIN block (before encryption).
     * Format: 0L PPPP PPPP PPPP FFFF
     * Where: 0 = format, L = PIN length, P = PIN digits, F = padding (random or zeros for test)
     * Then XOR with rightmost 12 digits of PAN (excluding check digit)
     */
    private static String buildIsoFormat0PinBlock(String clearPin, String pan) {
        // Build clear PIN field: 0 + length + PIN + padding
        StringBuilder pinField = new StringBuilder();
        pinField.append('0'); // Format 0
        pinField.append(String.format("%01d", clearPin.length())); // PIN length
        pinField.append(clearPin); // PIN

        // Pad to 16 hex digits with 'F'
        while (pinField.length() < 16) {
            pinField.append('F');
        }

        // Build PAN field: 0000 + rightmost 12 digits of PAN (excluding check digit)
        String panField = "0000" + pan.substring(pan.length() - 13, pan.length() - 1);

        // XOR pinField with panField
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            int pinNibble = Character.digit(pinField.charAt(i), 16);
            int panNibble = Character.digit(panField.charAt(i), 16);
            result.append(Integer.toHexString(pinNibble ^ panNibble).toUpperCase());
        }

        return result.toString();
    }

    /**
     * Simplified encryption using TPK.
     * In real scenarios, use proper 3DES encryption with proper key handling.
     */
    private static byte[] encryptWithTpk(byte[] data, String tpkHex) throws Exception {
        byte[] keyBytes = hexToBytes(tpkHex);

        // For 3DES, we need 24 bytes (192 bits)
        // If key is 16 bytes (128 bits), expand it
        if (keyBytes.length == 16) {
            byte[] key24 = new byte[24];
            System.arraycopy(keyBytes, 0, key24, 0, 16);
            System.arraycopy(keyBytes, 0, key24, 16, 8);
            keyBytes = key24;
        }

        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "DESede");
        Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);

        return cipher.doFinal(data);
    }

    /**
     * Convert hex string to byte array.
     */
    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * Convert byte array to hex string.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02X", b));
        }
        return result.toString();
    }
}
