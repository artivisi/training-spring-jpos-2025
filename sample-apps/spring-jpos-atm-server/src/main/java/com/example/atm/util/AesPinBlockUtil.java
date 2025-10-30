package com.example.atm.util;

import com.example.atm.dto.hsm.PinFormat;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import java.security.Security;

/**
 * Utility class for AES PIN block encryption and decryption.
 * Supports ISO 9564 PIN block formats with AES-128 and AES-256 encryption using CBC mode.
 * PIN blocks are exactly 16 bytes (one AES block), so NoPadding is used.
 * Encrypted output format: IV (16 bytes) + Ciphertext (16 bytes) = 32 bytes total = 64 hex chars.
 * Matches HSM Simulator encryption: AES-CBC with random IV prepended.
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
     * Encrypt a PIN block using AES/CBC/PKCS5Padding.
     * Output format: IV (16 bytes) || Ciphertext (16 bytes) = 32 bytes total.
     *
     * @param clearPinBlock 16-byte clear PIN block (ISO format)
     * @param tpkBytes AES Terminal PIN Key (16 bytes for AES-128, 32 bytes for AES-256)
     * @param bankUuid Bank UUID for key derivation context
     * @return 32-byte encrypted output (IV + ciphertext)
     */
    public static byte[] encryptPinBlock(byte[] clearPinBlock, byte[] tpkMasterKeyBytes, String bankUuid) {
      // ISO-0 PIN block is 8 bytes (16 hex chars), not 16 bytes!
      if (clearPinBlock.length != 8) {
          throw new IllegalArgumentException("Clear PIN block must be 8 bytes (ISO-0), got: " + clearPinBlock.length);
      }
      if (tpkMasterKeyBytes.length != 32) {
          throw new IllegalArgumentException("TPK master key must be 32 bytes (AES-256), got: " + tpkMasterKeyBytes.length);
      }

      try {
          // Step 1: Derive operational key from master key
          String context = "TPK:" + bankUuid + ":PIN";
          byte[] tpkOperationalKey = CryptoUtil.deriveKeyFromParent(tpkMasterKeyBytes, context, 128); // 128 bits = 16 bytes

          // Step 2: Generate random IV (16 bytes)
          byte[] iv = new byte[16];
          new java.security.SecureRandom().nextBytes(iv);

          // Step 3: Encrypt with PKCS5Padding (to match HSM)
          SecretKey tpk = new SecretKeySpec(tpkOperationalKey, "AES"); // Use derived key!
          Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding"); // Changed from NoPadding!
          cipher.init(Cipher.ENCRYPT_MODE, tpk, new javax.crypto.spec.IvParameterSpec(iv));

          // PKCS5Padding will pad 8 bytes → 16 bytes automatically
          byte[] ciphertext = cipher.doFinal(clearPinBlock);

          // Step 4: Prepend IV to ciphertext: IV || ciphertext
          byte[] result = new byte[iv.length + ciphertext.length];
          System.arraycopy(iv, 0, result, 0, iv.length);
          System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);

          return result; // 16 (IV) + 16 (encrypted) = 32 bytes total
      } catch (Exception e) {
          log.error("Failed to encrypt PIN block with TPK", e);
          throw new RuntimeException("TPK PIN block encryption failed", e);
      }
  }

    /**
   * Decrypt a PIN block using AES-128-CBC with PKCS5Padding.
   * Input format: IV (16 bytes) || Ciphertext (16 bytes) = 32 bytes total.
   *
   * @param encryptedPinBlock 32-byte encrypted input (IV + ciphertext)
   * @param tpkMasterKeyBytes TPK master key (32 bytes for AES-256)
   * @param bankUuid Bank UUID for key derivation context
   * @return 8-byte clear PIN block (ISO-0 format)
   */
  public static byte[] decryptPinBlock(byte[] encryptedPinBlock, byte[] tpkMasterKeyBytes, String bankUuid) {
      if (encryptedPinBlock.length != 32) {
          throw new IllegalArgumentException("Encrypted PIN block must be 32 bytes (IV + ciphertext), got: " +
  encryptedPinBlock.length);
      }
      if (tpkMasterKeyBytes.length != 32) {
          throw new IllegalArgumentException("TPK master key must be 32 bytes (AES-256), got: " + tpkMasterKeyBytes.length);
      }

      try {
          // Step 1: Derive operational key from master key
          String context = "TPK:" + bankUuid + ":PIN";
          byte[] tpkOperationalKey = CryptoUtil.deriveKeyFromParent(tpkMasterKeyBytes, context, 128); // 16 bytes

          // Step 2: Extract IV (first 16 bytes) and ciphertext (last 16 bytes)
          byte[] iv = new byte[16];
          byte[] ciphertext = new byte[16];
          System.arraycopy(encryptedPinBlock, 0, iv, 0, 16);
          System.arraycopy(encryptedPinBlock, 16, ciphertext, 0, 16);

          // Step 3: Decrypt with PKCS5Padding
          SecretKey tpk = new SecretKeySpec(tpkOperationalKey, "AES"); // Use derived key!
          Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding"); // Changed from NoPadding!
          cipher.init(Cipher.DECRYPT_MODE, tpk, new javax.crypto.spec.IvParameterSpec(iv));

          // PKCS5Padding will remove padding: 16 bytes → 8 bytes
          return cipher.doFinal(ciphertext); // Returns 8 bytes
      } catch (Exception e) {
          log.error("Failed to decrypt PIN block with TPK", e);
          throw new RuntimeException("TPK PIN block decryption failed", e);
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
     * Returns 8 bytes (64 bits).
     */
    private static byte[] buildIso0PinBlock(String pin, String pan) {
        if (pin.length() < 4 || pin.length() > 12) {
            throw new IllegalArgumentException("PIN length must be 4-12 digits");
        }
        if (pan == null || pan.length() < 13) {
            throw new IllegalArgumentException("PAN must be at least 13 digits");
        }

        byte[] pinBlock = new byte[8]; // ISO-0 is 8 bytes!

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
        byte[] panPart = new byte[8]; // 8 bytes!
        String panDigits = pan.substring(pan.length() - 13, pan.length() - 1); // Exclude check digit
        for (int i = 0; i < 12; i++) {
            int digit = Character.digit(panDigits.charAt(i), 10);
            if (i % 2 == 0) {
                panPart[2 + i / 2] = (byte) (digit << 4);
            } else {
                panPart[2 + i / 2] |= (byte) digit;
            }
        }

        // XOR the two parts
        for (int i = 0; i < 8; i++) {
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
