package com.example.atm.jpos.participant;

import java.io.Serializable;
import java.util.List;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.transaction.Context;
import org.jpos.transaction.TransactionParticipant;

import com.example.atm.config.HsmProperties;
import com.example.atm.entity.CryptoKey;
import com.example.atm.jpos.SpringBeanFactory;
import com.example.atm.service.CryptoKeyService;
import com.example.atm.util.AesCmacUtil;
import com.example.atm.util.CryptoUtil;

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

    private CryptoKeyService getCryptoKeyService() {
        return SpringBeanFactory.getBean(CryptoKeyService.class);
    }

    /**
     * Get terminal ID from ISO message fields 42 + 41.
     * Combines institution code (field 42) with terminal ID (field 41).
     * Tries REQUEST first, then RESPONSE if REQUEST not available.
     */
    private String getTerminalId(Context ctx) {
        try {
            // Try REQUEST first (prepare phase)
            ISOMsg msg = (ISOMsg) ctx.get("REQUEST");

            // Try RESPONSE if REQUEST not available (commit phase)
            if (msg == null) {
                msg = (ISOMsg) ctx.get("RESPONSE");
            }

            if (msg == null) {
                log.warn("No ISO message in context, using default terminal ID");
                return "TRM-ISS001-ATM-001";
            }

            // Build full terminal ID from field 42 (institution) + field 41 (terminal)
            String cardAcceptorId = msg.getString(42);  // e.g., "TRM-ISS001"
            String terminalId = msg.getString(41);       // e.g., "ATM-001"

            if (terminalId == null || terminalId.trim().isEmpty()) {
                log.warn("Terminal ID not found in field 41, using default");
                return "TRM-ISS001-ATM-001";
            }

            String fullTerminalId = (cardAcceptorId != null && !cardAcceptorId.trim().isEmpty())
                    ? cardAcceptorId.trim() + "-" + terminalId.trim()
                    : terminalId.trim();

            log.debug("Extracted terminal ID: {}", fullTerminalId);
            return fullTerminalId;

        } catch (Exception e) {
            log.error("Error extracting terminal ID from message: {}", e.getMessage());
            return "TRM-ISS001-ATM-001";
        }
    }

    /**
     * Try to verify MAC with a specific key.
     */
    private boolean tryVerifyMacWithKey(byte[] data, byte[] receivedMac, CryptoKey key, HsmProperties.MacAlgorithm algorithm) {
        byte[] keyBytes = CryptoUtil.hexToBytes(key.getKeyValue());
        String bankUuid = key.getBankUuid();

        return switch (algorithm) {
            case AES_CMAC -> AesCmacUtil.verifyMacWithKeyDerivation(data, receivedMac, keyBytes, bankUuid);
            case HMAC_SHA256_TRUNCATED -> {
                String context = "TSK:" + bankUuid + ":MAC";
                byte[] tskOperationalKey = CryptoUtil.deriveKeyFromParent(keyBytes, context, 128);
                yield AesCmacUtil.verifyHmacSha256Truncated(data, receivedMac, tskOperationalKey);
            }
        };
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
            boolean macValid = verifyMac(macData, receivedMac, macConfig.getAlgorithm(), ctx);

            if (!macValid) {
                log.error("MAC verification failed for transaction {}", id);
                ctx.put("RESPONSE_CODE", "96"); // System malfunction
                return PREPARED | NO_JOIN | READONLY;
            }

            log.info("MAC verification successful for transaction {}", id);
            ctx.put("MAC_VERIFIED", true);
            // Remove NO_JOIN to allow commit() phase for response MAC generation
            return PREPARED | READONLY;

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
            byte[] mac = generateMac(macData, macConfig.getAlgorithm(), ctx);

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
     * Tries ACTIVE key first, then PENDING keys if ACTIVE fails.
     * Tracks which key version was used for MAC generation in response.
     */
    private boolean verifyMac(byte[] data, byte[] receivedMac, HsmProperties.MacAlgorithm algorithm, Context ctx) {
        String terminalId = getTerminalId(ctx);

        // Try ACTIVE key first
        try {
            CryptoKey activeKey = getCryptoKeyService().getActiveKey(terminalId, CryptoKey.KeyType.TSK);
            if (tryVerifyMacWithKey(data, receivedMac, activeKey, algorithm)) {
                log.debug("MAC verified with ACTIVE TSK key version: {}", activeKey.getKeyVersion());
                ctx.put("TSK_KEY_VERSION_USED", activeKey.getKeyVersion());
                return true;
            }
        } catch (Exception e) {
            log.warn("Failed to verify MAC with ACTIVE key: {}", e.getMessage());
        }

        // Try PENDING keys (during grace period)
        try {
            List<CryptoKey> validKeys = getCryptoKeyService().getValidKeys(terminalId, CryptoKey.KeyType.TSK);
            for (CryptoKey key : validKeys) {
                if (key.getStatus() == CryptoKey.KeyStatus.PENDING) {
                    if (tryVerifyMacWithKey(data, receivedMac, key, algorithm)) {
                        log.info("MAC verified with PENDING TSK key version: {}",
                                key.getKeyVersion());
                        ctx.put("TSK_KEY_VERSION_USED", key.getKeyVersion());
                        // Note: PENDING key will only be activated upon explicit confirmation
                        // (operation codes 03/04) via KeyActivationParticipant
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error trying PENDING keys: {}", e.getMessage(), e);
        }

        log.error("MAC verification failed with all available keys");
        return false;
    }

    /**
     * Generate MAC using configured algorithm with TSK key derivation.
     * Uses the same key version that was used for request verification.
     */
    private byte[] generateMac(byte[] data, HsmProperties.MacAlgorithm algorithm, Context ctx) {
        String terminalId = getTerminalId(ctx);

        // Use the same key version that was used for request verification
        Integer keyVersionUsed = (Integer) ctx.get("TSK_KEY_VERSION_USED");
        CryptoKey tskKey;

        if (keyVersionUsed != null) {
            // Use the specific version that verified the request
            log.debug("Generating MAC using TSK key version: {}", keyVersionUsed);
            tskKey = getCryptoKeyService().getKeyByVersion(
                    terminalId, CryptoKey.KeyType.TSK, keyVersionUsed);
        } else {
            // Fallback to active key if version not tracked
            log.debug("Generating MAC using ACTIVE TSK key (no version tracked)");
            tskKey = getCryptoKeyService().getActiveKey(terminalId, CryptoKey.KeyType.TSK);
        }

        byte[] tskMasterKeyBytes = CryptoUtil.hexToBytes(tskKey.getKeyValue());
        String bankUuid = tskKey.getBankUuid();

        log.debug("SERVER MAC generation details:");
        log.debug("  MAC data length: {} bytes", data.length);
        log.debug("  MAC data (first 32 bytes): {}", CryptoUtil.bytesToHex(java.util.Arrays.copyOf(data, Math.min(32, data.length))));
        log.debug("  TSK key version: {}", tskKey.getKeyVersion());
        log.debug("  TSK key status: {}", tskKey.getStatus());
        log.debug("  Bank UUID: {}", bankUuid);

        return switch (algorithm) {
            case AES_CMAC -> AesCmacUtil.generateMacWithKeyDerivation(data, tskMasterKeyBytes, bankUuid);
            case HMAC_SHA256_TRUNCATED -> {
                // For HMAC, still use key derivation for consistency
                String context = "TSK:" + bankUuid + ":MAC";
                byte[] tskOperationalKey = CryptoUtil.deriveKeyFromParent(tskMasterKeyBytes, context, 128);
                yield AesCmacUtil.generateHmacSha256Truncated(data, tskOperationalKey);
            }
        };
    }

}
