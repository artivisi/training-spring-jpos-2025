package com.artivisi.atm.util;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;

@Slf4j
public class CryptoUtil {

    private CryptoUtil() {
        // Utility class
    }

    public static byte[] deriveKeyFromParent(byte[] parentKey, String context, int outputBits) {
        try {
            char[] keyChars = bytesToHex(parentKey).toCharArray();
            byte[] salt = context.getBytes(StandardCharsets.UTF_8);

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
