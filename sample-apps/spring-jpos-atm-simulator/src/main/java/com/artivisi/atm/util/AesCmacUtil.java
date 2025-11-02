package com.artivisi.atm.util;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.macs.CMac;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Security;

@Slf4j
public class AesCmacUtil {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private AesCmacUtil() {
        // Utility class
    }

    public static byte[] generateMacWithKeyDerivation(byte[] data, byte[] tskMasterKeyBytes, String bankUuid) {
        if (tskMasterKeyBytes.length != 32) {
            throw new IllegalArgumentException("TSK master key must be 32 bytes (AES-256), got: " + tskMasterKeyBytes.length);
        }

        String context = "TSK:" + bankUuid + ":MAC";
        byte[] tskOperationalKey = CryptoUtil.deriveKeyFromParent(tskMasterKeyBytes, context, 128);

        return generateMac(data, tskOperationalKey);
    }

    public static byte[] generateMac(byte[] data, byte[] keyBytes) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Data cannot be null or empty");
        }
        if (keyBytes.length != 16 && keyBytes.length != 32) {
            throw new IllegalArgumentException("AES key must be 16 or 32 bytes, got: " + keyBytes.length);
        }

        try {
            CMac cmac = new CMac(new AESEngine());
            CipherParameters params = new KeyParameter(keyBytes);
            cmac.init(params);

            cmac.update(data, 0, data.length);

            byte[] mac = new byte[cmac.getMacSize()];
            cmac.doFinal(mac, 0);

            log.debug("Generated AES-CMAC: {} bytes", mac.length);
            return mac;
        } catch (Exception e) {
            log.error("Failed to generate AES-CMAC", e);
            throw new RuntimeException("AES-CMAC generation failed", e);
        }
    }
}
