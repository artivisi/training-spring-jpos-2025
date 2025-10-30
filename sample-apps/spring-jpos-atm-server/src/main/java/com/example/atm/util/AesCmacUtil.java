package com.example.atm.util;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.macs.CMac;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Security;

/**
 * Utility class for AES-CMAC (Cipher-based Message Authentication Code).
 * Produces 16-byte (128-bit) MACs suitable for field 64 in ISO-8583 messages.
 */
@Slf4j
public class AesCmacUtil {

    static {
        // Register BouncyCastle provider
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private AesCmacUtil() {
        // Utility class
    }

    /**
     * Generate AES-CMAC with key derivation (recommended for production).
     * Derives 128-bit operational key from 256-bit master key using PBKDF2.
     *
     * @param data Data to authenticate
     * @param tskMasterKeyBytes 32-byte TSK master key
     * @param bankUuid Bank UUID for key derivation context
     * @return 16-byte MAC suitable for field 64
     */
    public static byte[] generateMacWithKeyDerivation(byte[] data, byte[] tskMasterKeyBytes, String bankUuid) {
        if (tskMasterKeyBytes.length != 32) {
            throw new IllegalArgumentException("TSK master key must be 32 bytes (AES-256), got: " + tskMasterKeyBytes.length);
        }

        // Derive operational key: TSK master (32 bytes) → operational (16 bytes)
        String context = "TSK:" + bankUuid + ":MAC";
        byte[] tskOperationalKey = deriveKeyFromParent(tskMasterKeyBytes, context, 128);

        return generateMac(data, tskOperationalKey);
    }

    /**
     * Verify AES-CMAC with key derivation.
     *
     * @param data Data to verify
     * @param receivedMac MAC received (16 bytes)
     * @param tskMasterKeyBytes 32-byte TSK master key
     * @param bankUuid Bank UUID for key derivation context
     * @return true if MAC is valid
     */
    public static boolean verifyMacWithKeyDerivation(byte[] data, byte[] receivedMac, byte[] tskMasterKeyBytes, String bankUuid) {
        if (receivedMac == null || receivedMac.length != 16) {
            throw new IllegalArgumentException("MAC must be 16 bytes");
        }

        try {
            byte[] calculatedMac = generateMacWithKeyDerivation(data, tskMasterKeyBytes, bankUuid);
            return MessageDigest.isEqual(calculatedMac, receivedMac);
        } catch (Exception e) {
            log.error("Failed to verify AES-CMAC with key derivation", e);
            return false;
        }
    }

    /**
     * Generate AES-CMAC (128-bit output) for the given data.
     * Note: This method uses the key directly. For production, use generateMacWithKeyDerivation().
     *
     * @param data Data to authenticate
     * @param keyBytes 16-byte AES-128 key (or 32-byte AES-256)
     * @return 16-byte MAC suitable for field 64
     */
    public static byte[] generateMac(byte[] data, byte[] keyBytes) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Data cannot be null or empty");
        }
        if (keyBytes.length != 16 && keyBytes.length != 32) {
            throw new IllegalArgumentException("AES key must be 16 or 32 bytes, got: " + keyBytes.length);
        }

        try {
            // Create AES-CMAC instance
            CMac cmac = new CMac(new AESEngine());
            CipherParameters params = new KeyParameter(keyBytes);
            cmac.init(params);

            // Process data
            cmac.update(data, 0, data.length);

            // Get MAC (16 bytes)
            byte[] mac = new byte[cmac.getMacSize()];
            cmac.doFinal(mac, 0);

            log.debug("Generated AES-CMAC: {} bytes", mac.length);
            return mac;
        } catch (Exception e) {
            log.error("Failed to generate AES-CMAC", e);
            throw new RuntimeException("AES-CMAC generation failed", e);
        }
    }

    /**
     * Derive operational key from parent key using PBKDF2-SHA256.
     * Matches HSM simulator key derivation: 100,000 iterations, context as salt.
     *
     * @param parentKey Parent key bytes (e.g., 32-byte master key)
     * @param context Context string (e.g., "TSK:UUID:MAC")
     * @param outputBits Output key size in bits (e.g., 128 for 16 bytes)
     * @return Derived key bytes
     */
    private static byte[] deriveKeyFromParent(byte[] parentKey, String context, int outputBits) {
        try {
            // Convert parent key to char array (hex representation)
            char[] keyChars = bytesToHex(parentKey).toCharArray();

            // Use context as salt
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
     * Convert byte array to hex string.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02X", b));
        }
        return result.toString();
    }

    /**
     * Verify AES-CMAC for the given data.
     *
     * @param data Data to verify
     * @param receivedMac MAC received (16 bytes)
     * @param tmkBytes AES key (16 or 32 bytes)
     * @return true if MAC is valid
     */
    public static boolean verifyMac(byte[] data, byte[] receivedMac, byte[] tmkBytes) {
        if (receivedMac == null || receivedMac.length != 16) {
            throw new IllegalArgumentException("MAC must be 16 bytes");
        }

        try {
            byte[] calculatedMac = generateMac(data, tmkBytes);
            return MessageDigest.isEqual(calculatedMac, receivedMac);
        } catch (Exception e) {
            log.error("Failed to verify AES-CMAC", e);
            return false;
        }
    }

    /**
     * Generate truncated HMAC-SHA256 (truncated to 16 bytes for field 64 compatibility).
     * Note: Full HMAC-SHA256 is 32 bytes. This method truncates to 16 bytes.
     *
     * @param data Data to authenticate
     * @param tmkBytes HMAC key (can be any length)
     * @return 16-byte truncated HMAC-SHA256
     */
    public static byte[] generateHmacSha256Truncated(byte[] data, byte[] tmkBytes) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Data cannot be null or empty");
        }

        try {
            javax.crypto.Mac hmac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(tmkBytes, "HmacSHA256");
            hmac.init(keySpec);

            byte[] fullMac = hmac.doFinal(data);
            // Truncate to 16 bytes for field 64
            byte[] truncatedMac = new byte[16];
            System.arraycopy(fullMac, 0, truncatedMac, 0, 16);

            log.debug("Generated HMAC-SHA256 (truncated): {} bytes", truncatedMac.length);
            return truncatedMac;
        } catch (Exception e) {
            log.error("Failed to generate HMAC-SHA256", e);
            throw new RuntimeException("HMAC-SHA256 generation failed", e);
        }
    }

    /**
     * Verify truncated HMAC-SHA256.
     *
     * @param data Data to verify
     * @param receivedMac MAC received (16 bytes)
     * @param tmkBytes HMAC key
     * @return true if MAC is valid
     */
    public static boolean verifyHmacSha256Truncated(byte[] data, byte[] receivedMac, byte[] tmkBytes) {
        if (receivedMac == null || receivedMac.length != 16) {
            throw new IllegalArgumentException("MAC must be 16 bytes");
        }

        try {
            byte[] calculatedMac = generateHmacSha256Truncated(data, tmkBytes);
            return MessageDigest.isEqual(calculatedMac, receivedMac);
        } catch (Exception e) {
            log.error("Failed to verify HMAC-SHA256", e);
            return false;
        }
    }
}
