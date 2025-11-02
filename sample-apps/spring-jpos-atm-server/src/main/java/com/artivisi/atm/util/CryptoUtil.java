package com.artivisi.atm.util;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * Common cryptographic utility methods.
 * Provides key derivation, encryption/decryption, and encoding conversion functions.
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

    /**
     * Decrypt a new key received from HSM during key rotation.
     * The encrypted key is encrypted under the current master key using:
     * - Context: "KEY_DELIVERY:ROTATION"
     * - Algorithm: AES-128-CBC with PKCS5 padding
     * - Format: IV (16 bytes) || ciphertext
     *
     * @param encryptedKeyHex Encrypted key in hex format (IV + ciphertext)
     * @param currentMasterKey Current master key bytes (32 bytes for AES-256)
     * @return Decrypted new key bytes (32 bytes)
     */
    public static byte[] decryptRotationKey(String encryptedKeyHex, byte[] currentMasterKey) {
        try {
            if (currentMasterKey.length != 32) {
                throw new IllegalArgumentException("Current master key must be 32 bytes (AES-256)");
            }

            // Convert hex to bytes
            byte[] encryptedWithIv = hexToBytes(encryptedKeyHex);

            // Derive operational decryption key
            String context = "KEY_DELIVERY:ROTATION";
            byte[] operationalKey = deriveKeyFromParent(currentMasterKey, context, 128); // 128 bits = 16 bytes

            // Extract IV (first 16 bytes) and ciphertext (remaining bytes)
            byte[] iv = Arrays.copyOfRange(encryptedWithIv, 0, 16);
            byte[] ciphertext = Arrays.copyOfRange(encryptedWithIv, 16, encryptedWithIv.length);

            // Decrypt using AES-128-CBC with PKCS5 padding
            SecretKey key = new SecretKeySpec(operationalKey, "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));

            byte[] decrypted = cipher.doFinal(ciphertext);
            log.debug("Successfully decrypted rotation key: {} bytes", decrypted.length);

            return decrypted;
        } catch (Exception e) {
            log.error("Failed to decrypt rotation key: {}", e.getMessage(), e);
            throw new RuntimeException("Rotation key decryption failed", e);
        }
    }

    /**
     * Calculate checksum of a key for verification.
     * Uses SHA-256 and returns first 16 hex characters (uppercase).
     *
     * @param keyBytes Key bytes to checksum
     * @return First 16 hex chars of SHA-256 hash (uppercase)
     */
    public static String calculateKeyChecksum(byte[] keyBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(keyBytes);
            String fullHex = bytesToHex(hash);

            // Return first 16 hex characters
            String checksum = fullHex.substring(0, 16);
            log.debug("Calculated key checksum: {}", checksum);

            return checksum;
        } catch (Exception e) {
            log.error("Failed to calculate key checksum: {}", e.getMessage(), e);
            throw new RuntimeException("Key checksum calculation failed", e);
        }
    }

    /**
     * Verify that a decrypted key matches the expected checksum.
     *
     * @param decryptedKey Decrypted key bytes
     * @param expectedChecksum Expected checksum from HSM (16 hex chars)
     * @return true if checksums match, false otherwise
     */
    public static boolean verifyKeyChecksum(byte[] decryptedKey, String expectedChecksum) {
        String actualChecksum = calculateKeyChecksum(decryptedKey);
        boolean matches = actualChecksum.equalsIgnoreCase(expectedChecksum);

        if (!matches) {
            log.error("Key checksum mismatch! Expected: {}, Actual: {}",
                     expectedChecksum, actualChecksum);
        } else {
            log.info("Key checksum verified successfully: {}", actualChecksum);
        }

        return matches;
    }
}
