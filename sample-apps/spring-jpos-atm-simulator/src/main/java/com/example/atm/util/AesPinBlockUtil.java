package com.example.atm.util;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.Security;

@Slf4j
public class AesPinBlockUtil {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private AesPinBlockUtil() {
        // Utility class
    }

    public static byte[] encryptPinBlock(byte[] clearPinBlock, byte[] tpkMasterKeyBytes, String bankUuid) {
        if (clearPinBlock.length != 8) {
            throw new IllegalArgumentException("Clear PIN block must be 8 bytes (ISO-0), got: " + clearPinBlock.length);
        }
        if (tpkMasterKeyBytes.length != 32) {
            throw new IllegalArgumentException("TPK master key must be 32 bytes (AES-256), got: " + tpkMasterKeyBytes.length);
        }

        try {
            String context = "TPK:" + bankUuid + ":PIN";
            byte[] tpkOperationalKey = CryptoUtil.deriveKeyFromParent(tpkMasterKeyBytes, context, 128);

            byte[] iv = new byte[16];
            new java.security.SecureRandom().nextBytes(iv);

            SecretKey tpk = new SecretKeySpec(tpkOperationalKey, "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, tpk, new javax.crypto.spec.IvParameterSpec(iv));

            byte[] ciphertext = cipher.doFinal(clearPinBlock);

            byte[] result = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);

            return result;
        } catch (Exception e) {
            log.error("Failed to encrypt PIN block with TPK", e);
            throw new RuntimeException("TPK PIN block encryption failed", e);
        }
    }

    public static byte[] buildIso0PinBlock(String pin, String pan) {
        if (pin.length() < 4 || pin.length() > 12) {
            throw new IllegalArgumentException("PIN length must be 4-12 digits");
        }
        if (pan == null || pan.length() < 13) {
            throw new IllegalArgumentException("PAN must be at least 13 digits");
        }

        byte[] pinBlock = new byte[8];

        pinBlock[0] = (byte) (0x00 | pin.length());
        for (int i = 0; i < pin.length(); i++) {
            int digit = Character.digit(pin.charAt(i), 10);
            if (i % 2 == 0) {
                pinBlock[1 + i / 2] = (byte) (digit << 4);
            } else {
                pinBlock[1 + i / 2] |= (byte) digit;
            }
        }
        for (int i = pin.length(); i < 14; i++) {
            if (i % 2 == 0) {
                pinBlock[1 + i / 2] |= (byte) 0xF0;
            } else {
                pinBlock[1 + i / 2] |= (byte) 0x0F;
            }
        }

        byte[] panPart = new byte[8];
        String panDigits = pan.substring(pan.length() - 13, pan.length() - 1);
        for (int i = 0; i < 12; i++) {
            int digit = Character.digit(panDigits.charAt(i), 10);
            if (i % 2 == 0) {
                panPart[2 + i / 2] = (byte) (digit << 4);
            } else {
                panPart[2 + i / 2] |= (byte) digit;
            }
        }

        for (int i = 0; i < 8; i++) {
            pinBlock[i] ^= panPart[i];
        }

        return pinBlock;
    }
}
