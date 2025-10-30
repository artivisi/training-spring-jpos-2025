package com.example.atm.util;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Common cryptographic utility methods.
 * Provides key derivation and encoding conversion functions used across the application.
 */
@Slf4j
public class CryptoUtil {

    private CryptoUtil() {
        // Utility class
    }

    /**
     * Derive operational key from parent key using PBKDF2-SHA256.
     * Matches HSM simulator key derivation: 100,000 iterations, context as salt.
     *
     * Used for deriving TPK and TSK operational keys from master keys.
     *
     * @param parentKey Parent key bytes (e.g., 32-byte master key)
     * @param context Context string for key separation (e.g., "TSK:UUID:MAC", "TPK:UUID:PIN")
     * @param outputBits Output key size in bits (e.g., 128 for 16 bytes)
     * @return Derived key bytes
     */
    public static byte[] deriveKeyFromParent(byte[] parentKey, String context, int outputBits) {
        try {
            // Convert parent key to char array (hex representation)
            char[] keyChars = bytesToHex(parentKey).toCharArray();

            // Use context as salt for key separation
            byte[] salt = context.getBytes(StandardCharsets.UTF_8);

            // PBKDF2 with 100,000 iterations (matches HSM)
            PBEKeySpec spec = new PBEKeySpec(keyChars, salt, 100_000, outputBits);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

            byte[] derived = factory.generateSecret(spec).getEncoded();
            log.debug("Derived {}-bit key from {}-byte parent key using context: {}",
                     outputBits, parentKey.length, context);
            return derived;
        } catch (Exception e) {
            throw new RuntimeException("Key derivation failed", e);
        }
    }

    /**
     * Convert hex string to byte array.
     *
     * @param hex Hex string (e.g., "AABBCCDD")
     * @return Byte array
     */
    public static byte[] hexToBytes(String hex) {
        if (hex == null) {
            throw new IllegalArgumentException("Hex string cannot be null");
        }
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex string must have even length");
        }

        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * Convert byte array to hex string (uppercase).
     *
     * @param bytes Byte array
     * @return Hex string (uppercase)
     */
    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("Byte array cannot be null");
        }

        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02X", b));
        }
        return result.toString();
    }
}
