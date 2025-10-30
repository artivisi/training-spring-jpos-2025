package com.example.atm.jpos.participant;

import java.io.Serializable;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.jpos.transaction.Context;
import org.jpos.transaction.TransactionParticipant;

import com.example.atm.config.HsmProperties;
import com.example.atm.jpos.SpringBeanFactory;
import com.example.atm.util.AesCmacUtil;
import com.example.atm.util.AesPinBlockUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * jPOS TransactionParticipant for MAC (Message Authentication Code) verification.
 * Verifies MAC in field 64 for incoming requests and generates MAC for responses.
 * Supports AES-CMAC and HMAC-SHA256 (truncated to 16 bytes).
 * Note: This class is NOT managed by Spring - it's instantiated by jPOS Q2.
 */
@Slf4j
public class MacVerificationParticipant implements TransactionParticipant {

    private HsmProperties getHsmProperties() {
        return SpringBeanFactory.getBean(HsmProperties.class);
    }

    /**
     * Get TSK master key bytes from configuration.
     */
    private byte[] getTskMasterKeyBytes() {
        HsmProperties.Keys keys = getHsmProperties().getKeys();
        if (keys == null || keys.getTskMasterKey() == null) {
            throw new RuntimeException("TSK master key not configured");
        }
        return AesPinBlockUtil.hexToBytes(keys.getTskMasterKey());
    }

    /**
     * Get bank UUID from configuration.
     */
    private String getBankUuid() {
        HsmProperties.Keys keys = getHsmProperties().getKeys();
        if (keys == null || keys.getBankUuid() == null) {
            throw new RuntimeException("Bank UUID not configured");
        }
        return keys.getBankUuid();
    }

    @Override
    public int prepare(long id, Serializable context) {
        Context ctx = (Context) context;
        try {
            ISOMsg msg = (ISOMsg) ctx.get("REQUEST");

            if (msg == null) {
                log.error("No ISO message in context");
                ctx.put("RESPONSE_CODE", "96");
                return PREPARED | NO_JOIN | READONLY;
            }

            HsmProperties.Mac macConfig = getHsmProperties().getMac();
            if (macConfig == null || !macConfig.isVerifyEnabled()) {
                log.debug("MAC verification disabled, skipping");
                return PREPARED | NO_JOIN | READONLY;
            }

            // Check if MAC field is present
            if (!msg.hasField(64)) {
                log.debug("No MAC in field 64, skipping verification");
                return PREPARED | NO_JOIN | READONLY;
            }

            byte[] receivedMac = msg.getBytes(64);
            log.debug("Received MAC from field 64: {} bytes", receivedMac.length);

            // Build MAC data from message fields (excluding field 64)
            byte[] macData = buildMacData(msg);

            // Verify MAC based on configured algorithm
            boolean macValid = verifyMac(macData, receivedMac, macConfig.getAlgorithm());

            if (!macValid) {
                log.error("MAC verification failed for transaction {}", id);
                ctx.put("RESPONSE_CODE", "96"); // System malfunction
                return PREPARED | NO_JOIN | READONLY;
            }

            log.info("MAC verification successful for transaction {}", id);
            ctx.put("MAC_VERIFIED", true);
            return PREPARED | NO_JOIN | READONLY;

        } catch (Exception e) {
            log.error("MAC verification error: {}", e.getMessage(), e);
            ctx.put("RESPONSE_CODE", "96");
            return PREPARED | NO_JOIN | READONLY;
        }
    }

    @Override
    public void commit(long id, Serializable context) {
        // Generate MAC for response if enabled
        Context ctx = (Context) context;
        try {
            HsmProperties.Mac macConfig = getHsmProperties().getMac();
            if (macConfig == null || !macConfig.isGenerateEnabled()) {
                log.debug("MAC generation disabled, skipping");
                return;
            }

            ISOMsg response = (ISOMsg) ctx.get("RESPONSE");
            if (response == null) {
                log.debug("No response message, skipping MAC generation");
                return;
            }

            // Build MAC data from response fields (excluding field 64)
            byte[] macData = buildMacData(response);

            // Generate MAC
            byte[] mac = generateMac(macData, macConfig.getAlgorithm());

            // Set MAC in field 64
            response.set(64, mac);
            log.info("Generated MAC for response: {} bytes", mac.length);

        } catch (Exception e) {
            log.error("Failed to generate MAC for response: {}", e.getMessage(), e);
        }
    }

    @Override
    public void abort(long id, Serializable context) {
        log.debug("MAC verification participant aborted for transaction: {}", id);
    }

    /**
     * Build MAC data from ISO message fields.
     * Typically includes all fields except the MAC field itself (field 64).
     */
    private byte[] buildMacData(ISOMsg msg) throws ISOException {
        // Clone message and remove MAC field
        ISOMsg msgCopy = (ISOMsg) msg.clone();
        msgCopy.unset(64);

        // Pack the message to get raw bytes
        byte[] packedMsg = msgCopy.pack();

        log.debug("Built MAC data: {} bytes", packedMsg.length);
        return packedMsg;
    }

    /**
     * Verify MAC using configured algorithm with TSK key derivation.
     */
    private boolean verifyMac(byte[] data, byte[] receivedMac, HsmProperties.MacAlgorithm algorithm) {
        byte[] tskMasterKeyBytes = getTskMasterKeyBytes();
        String bankUuid = getBankUuid();

        return switch (algorithm) {
            case AES_CMAC -> AesCmacUtil.verifyMacWithKeyDerivation(data, receivedMac, tskMasterKeyBytes, bankUuid);
            case HMAC_SHA256_TRUNCATED -> {
                // For HMAC, still use key derivation for consistency
                String context = "TSK:" + bankUuid + ":MAC";
                byte[] tskOperationalKey = deriveKeyFromParent(tskMasterKeyBytes, context, 128);
                yield AesCmacUtil.verifyHmacSha256Truncated(data, receivedMac, tskOperationalKey);
            }
        };
    }

    /**
     * Generate MAC using configured algorithm with TSK key derivation.
     */
    private byte[] generateMac(byte[] data, HsmProperties.MacAlgorithm algorithm) {
        byte[] tskMasterKeyBytes = getTskMasterKeyBytes();
        String bankUuid = getBankUuid();

        return switch (algorithm) {
            case AES_CMAC -> AesCmacUtil.generateMacWithKeyDerivation(data, tskMasterKeyBytes, bankUuid);
            case HMAC_SHA256_TRUNCATED -> {
                // For HMAC, still use key derivation for consistency
                String context = "TSK:" + bankUuid + ":MAC";
                byte[] tskOperationalKey = deriveKeyFromParent(tskMasterKeyBytes, context, 128);
                yield AesCmacUtil.generateHmacSha256Truncated(data, tskOperationalKey);
            }
        };
    }

    /**
     * Derive operational key from parent key using PBKDF2-SHA256.
     * Matches HSM simulator key derivation: 100,000 iterations, context as salt.
     */
    private byte[] deriveKeyFromParent(byte[] parentKey, String context, int outputBits) {
        try {
            // Convert parent key to char array (hex representation)
            char[] keyChars = AesPinBlockUtil.bytesToHex(parentKey).toCharArray();

            // Use context as salt
            byte[] salt = context.getBytes(java.nio.charset.StandardCharsets.UTF_8);

            // PBKDF2 with 100,000 iterations (matches HSM)
            javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(keyChars, salt, 100_000, outputBits);
            javax.crypto.SecretKeyFactory factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

            return factory.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new RuntimeException("Key derivation failed", e);
        }
    }
}
