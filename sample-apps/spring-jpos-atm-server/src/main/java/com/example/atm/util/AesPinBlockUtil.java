package com.example.atm.util;

import com.example.atm.dto.hsm.PinFormat;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.Security;

/**
 * Utility class for AES-128 PIN block encryption and decryption.
 * Supports ISO 9564 PIN block formats with AES-128 encryption.
 */
@Slf4j
public class AesPinBlockUtil {

    static {
        // Register BouncyCastle provider
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private AesPinBlockUtil() {
        // Utility class
    }

    /**
     * Encrypt a PIN block using AES-128.
     *
     * @param clearPinBlock 16-byte clear PIN block (ISO format)
     * @param tpkBytes 16-byte AES-128 Terminal PIN Key
     * @return 16-byte encrypted PIN block
     */
    public static byte[] encryptPinBlock(byte[] clearPinBlock, byte[] tpkBytes) {
        if (clearPinBlock.length != 16) {
            throw new IllegalArgumentException("Clear PIN block must be 16 bytes, got: " + clearPinBlock.length);
        }
        if (tpkBytes.length != 16) {
            throw new IllegalArgumentException("AES-128 TPK must be 16 bytes, got: " + tpkBytes.length);
        }

        try {
            SecretKey tpk = new SecretKeySpec(tpkBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, tpk);
            return cipher.doFinal(clearPinBlock);
        } catch (Exception e) {
            log.error("Failed to encrypt PIN block with AES-128", e);
            throw new RuntimeException("AES-128 PIN block encryption failed", e);
        }
    }

    /**
     * Decrypt a PIN block using AES-128.
     *
     * @param encryptedPinBlock 16-byte encrypted PIN block
     * @param tpkBytes 16-byte AES-128 Terminal PIN Key
     * @return 16-byte clear PIN block (ISO format)
     */
    public static byte[] decryptPinBlock(byte[] encryptedPinBlock, byte[] tpkBytes) {
        if (encryptedPinBlock.length != 16) {
            throw new IllegalArgumentException("Encrypted PIN block must be 16 bytes, got: " + encryptedPinBlock.length);
        }
        if (tpkBytes.length != 16) {
            throw new IllegalArgumentException("AES-128 TPK must be 16 bytes, got: " + tpkBytes.length);
        }

        try {
            SecretKey tpk = new SecretKeySpec(tpkBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, tpk);
            return cipher.doFinal(encryptedPinBlock);
        } catch (Exception e) {
            log.error("Failed to decrypt PIN block with AES-128", e);
            throw new RuntimeException("AES-128 PIN block decryption failed", e);
        }
    }

    /**
     * Build a clear PIN block according to ISO 9564 format.
     *
     * @param pin Clear PIN (4-12 digits)
     * @param pan Primary Account Number (PAN)
     * @param format PIN block format
     * @return 16-byte clear PIN block
     */
    public static byte[] buildClearPinBlock(String pin, String pan, PinFormat format) {
        return switch (format) {
            case ISO_0 -> buildIso0PinBlock(pin, pan);
            case ISO_1 -> buildIso1PinBlock(pin);
            case ISO_3 -> buildIso3PinBlock(pin);
            case ISO_4 -> buildIso4PinBlock(pin);
        };
    }

    /**
     * Build ISO-0 format PIN block (most common).
     * Format: 0L + PIN + F padding, XORed with 0000 + rightmost 12 digits of PAN (excluding check digit)
     */
    private static byte[] buildIso0PinBlock(String pin, String pan) {
        if (pin.length() < 4 || pin.length() > 12) {
            throw new IllegalArgumentException("PIN length must be 4-12 digits");
        }
        if (pan == null || pan.length() < 13) {
            throw new IllegalArgumentException("PAN must be at least 13 digits");
        }

        byte[] pinBlock = new byte[16];

        // First part: 0L + PIN + F padding
        pinBlock[0] = (byte) (0x00 | pin.length());
        for (int i = 0; i < pin.length(); i++) {
            int digit = Character.digit(pin.charAt(i), 10);
            if (i % 2 == 0) {
                pinBlock[1 + i / 2] = (byte) (digit << 4);
            } else {
                pinBlock[1 + i / 2] |= (byte) digit;
            }
        }
        // Fill remaining with F
        for (int i = pin.length(); i < 14; i++) {
            if (i % 2 == 0) {
                pinBlock[1 + i / 2] |= (byte) 0xF0;
            } else {
                pinBlock[1 + i / 2] |= (byte) 0x0F;
            }
        }

        // Second part: 0000 + rightmost 12 digits of PAN (excluding check digit)
        byte[] panPart = new byte[16];
        String panDigits = pan.substring(pan.length() - 13, pan.length() - 1); // Exclude check digit
        for (int i = 0; i < 12; i++) {
            int digit = Character.digit(panDigits.charAt(i), 10);
            if (i % 2 == 0) {
                panPart[4 + i / 2] = (byte) (digit << 4);
            } else {
                panPart[4 + i / 2] |= (byte) digit;
            }
        }

        // XOR the two parts
        for (int i = 0; i < 16; i++) {
            pinBlock[i] ^= panPart[i];
        }

        return pinBlock;
    }

    /**
     * Build ISO-1 format PIN block.
     * Format: 1L + PIN + random padding
     */
    private static byte[] buildIso1PinBlock(String pin) {
        if (pin.length() < 4 || pin.length() > 12) {
            throw new IllegalArgumentException("PIN length must be 4-12 digits");
        }

        byte[] pinBlock = new byte[16];
        pinBlock[0] = (byte) (0x10 | pin.length());

        for (int i = 0; i < pin.length(); i++) {
            int digit = Character.digit(pin.charAt(i), 10);
            if (i % 2 == 0) {
                pinBlock[1 + i / 2] = (byte) (digit << 4);
            } else {
                pinBlock[1 + i / 2] |= (byte) digit;
            }
        }

        // Fill remaining with random
        for (int i = pin.length(); i < 30; i++) {
            int randomDigit = (int) (Math.random() * 10);
            if (i % 2 == 0) {
                pinBlock[1 + i / 2] |= (byte) (randomDigit << 4);
            } else {
                pinBlock[1 + i / 2] |= (byte) randomDigit;
            }
        }

        return pinBlock;
    }

    /**
     * Build ISO-3 format PIN block.
     * Format: 3L + PIN + random digits
     */
    private static byte[] buildIso3PinBlock(String pin) {
        if (pin.length() < 4 || pin.length() > 12) {
            throw new IllegalArgumentException("PIN length must be 4-12 digits");
        }

        byte[] pinBlock = new byte[16];
        pinBlock[0] = (byte) (0x30 | pin.length());

        for (int i = 0; i < pin.length(); i++) {
            int digit = Character.digit(pin.charAt(i), 10);
            if (i % 2 == 0) {
                pinBlock[1 + i / 2] = (byte) (digit << 4);
            } else {
                pinBlock[1 + i / 2] |= (byte) digit;
            }
        }

        // Fill remaining with random digits
        for (int i = pin.length(); i < 30; i++) {
            int randomDigit = (int) (Math.random() * 10);
            if (i % 2 == 0) {
                pinBlock[1 + i / 2] |= (byte) (randomDigit << 4);
            } else {
                pinBlock[1 + i / 2] |= (byte) randomDigit;
            }
        }

        return pinBlock;
    }

    /**
     * Build ISO-4 format PIN block.
     * Format: 4L + PIN + random hex
     */
    private static byte[] buildIso4PinBlock(String pin) {
        if (pin.length() < 4 || pin.length() > 12) {
            throw new IllegalArgumentException("PIN length must be 4-12 digits");
        }

        byte[] pinBlock = new byte[16];
        pinBlock[0] = (byte) (0x40 | pin.length());

        for (int i = 0; i < pin.length(); i++) {
            int digit = Character.digit(pin.charAt(i), 10);
            if (i % 2 == 0) {
                pinBlock[1 + i / 2] = (byte) (digit << 4);
            } else {
                pinBlock[1 + i / 2] |= (byte) digit;
            }
        }

        // Fill remaining with random hex
        for (int i = pin.length(); i < 30; i++) {
            int randomHex = (int) (Math.random() * 16);
            if (i % 2 == 0) {
                pinBlock[1 + i / 2] |= (byte) (randomHex << 4);
            } else {
                pinBlock[1 + i / 2] |= (byte) randomHex;
            }
        }

        return pinBlock;
    }

    /**
     * Extract PIN from decrypted ISO-0 format PIN block.
     *
     * @param clearPinBlock Decrypted 16-byte PIN block
     * @param pan Primary Account Number
     * @return Clear PIN
     */
    public static String extractPinFromIso0Block(byte[] clearPinBlock, String pan) {
        if (clearPinBlock.length != 16) {
            throw new IllegalArgumentException("PIN block must be 16 bytes");
        }

        // XOR with PAN part to get the original PIN block
        byte[] panPart = new byte[16];
        String panDigits = pan.substring(pan.length() - 13, pan.length() - 1);
        for (int i = 0; i < 12; i++) {
            int digit = Character.digit(panDigits.charAt(i), 10);
            if (i % 2 == 0) {
                panPart[4 + i / 2] = (byte) (digit << 4);
            } else {
                panPart[4 + i / 2] |= (byte) digit;
            }
        }

        byte[] pinPart = new byte[16];
        for (int i = 0; i < 16; i++) {
            pinPart[i] = (byte) (clearPinBlock[i] ^ panPart[i]);
        }

        // Extract PIN length
        int pinLength = pinPart[0] & 0x0F;
        if (pinLength < 4 || pinLength > 12) {
            throw new IllegalArgumentException("Invalid PIN length: " + pinLength);
        }

        // Extract PIN digits
        StringBuilder pin = new StringBuilder();
        for (int i = 0; i < pinLength; i++) {
            int digit;
            if (i % 2 == 0) {
                digit = (pinPart[1 + i / 2] >> 4) & 0x0F;
            } else {
                digit = pinPart[1 + i / 2] & 0x0F;
            }
            pin.append(digit);
        }

        return pin.toString();
    }
}
