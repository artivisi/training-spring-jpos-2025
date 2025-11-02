package com.artivisi.atm.jpos.participant;

import com.artivisi.atm.dto.rotation.KeyRotationResponse;
import com.artivisi.atm.entity.CryptoKey;
import com.artivisi.atm.jpos.SpringBeanFactory;
import com.artivisi.atm.jpos.util.TerminalIdUtil;
import com.artivisi.atm.service.KeyRotationService;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOMsg;
import org.jpos.transaction.Context;
import org.jpos.transaction.TransactionParticipant;

import java.io.Serializable;

/**
 * jPOS TransactionParticipant for handling key change requests.
 * Processes MTI 0800 network management messages for key rotation.
 *
 * Supports both terminal-initiated and server-initiated key rotation.
 *
 * Field usage:
 * - Field 11: STAN (System Trace Audit Number)
 * - Field 53: Security Related Control Information
 *   Format: first 2 digits indicate operation
 *   01 = TPK key request (server sends encrypted new key)
 *   02 = TSK key request (server sends encrypted new key)
 *   03 = TPK installation confirmed (terminal confirms successful installation)
 *   04 = TSK installation confirmed (terminal confirms successful installation)
 *   05 = TPK installation failed (terminal reports failure)
 *   06 = TSK installation failed (terminal reports failure)
 *   07 = Server-initiated key change notification (terminal should initiate key change)
 * - Field 70: Network Management Information Code
 *   301 = Key change notification (used with operation 07)
 * - Field 123 (response): Encrypted new key (for operations 01/02)
 * - Field 39: Response code
 *
 * Note: This class is NOT managed by Spring - it's instantiated by jPOS Q2.
 */
@Slf4j
public class KeyChangeParticipant implements TransactionParticipant {

    private KeyRotationService getKeyRotationService() {
        return SpringBeanFactory.getBean(KeyRotationService.class);
    }

    @Override
    public int prepare(long id, Serializable context) {
        Context ctx = (Context) context;
        try {
            ISOMsg request = (ISOMsg) ctx.get("REQUEST");

            if (request == null) {
                log.error("No ISO message in context");
                ctx.put("RESPONSE_CODE", "96");
                return PREPARED | NO_JOIN | READONLY;
            }

            String mti = request.getMTI();

            // Only process network management messages (0800)
            if (!"0800".equals(mti)) {
                log.debug("Not a network management message, skipping: MTI={}", mti);
                return PREPARED | NO_JOIN | READONLY;
            }

            // Check if this is a key change message (field 53 present)
            String securityControl = request.getString(53);
            if (securityControl == null || securityControl.length() < 2) {
                // Not a key change message (e.g., sign-on uses field 70 instead)
                log.debug("No field 53 present, not a key change message");
                return PREPARED | NO_JOIN | READONLY;
            }

            String operationCode = securityControl.substring(0, 2);

            // Extract full terminal ID from ISO message fields
            String fullTerminalId = TerminalIdUtil.extractTerminalId(request);

            if (fullTerminalId == null || fullTerminalId.trim().isEmpty()) {
                log.error("Terminal ID not found in message fields");
                ctx.put("RESPONSE_CODE", "30");
                return PREPARED | NO_JOIN | READONLY;
            }

            // Route to appropriate handler based on operation code
            switch (operationCode) {
                case "01":
                    return handleKeyRequest(ctx, fullTerminalId, CryptoKey.KeyType.TPK, request);
                case "02":
                    return handleKeyRequest(ctx, fullTerminalId, CryptoKey.KeyType.TSK, request);
                case "03":
                    return handleKeyConfirmation(ctx, fullTerminalId, CryptoKey.KeyType.TPK, request);
                case "04":
                    return handleKeyConfirmation(ctx, fullTerminalId, CryptoKey.KeyType.TSK, request);
                case "05":
                    return handleKeyFailure(ctx, fullTerminalId, CryptoKey.KeyType.TPK, request);
                case "06":
                    return handleKeyFailure(ctx, fullTerminalId, CryptoKey.KeyType.TSK, request);
                default:
                    log.error("Unsupported security operation: {}", operationCode);
                    ctx.put("RESPONSE_CODE", "30");
                    return PREPARED | NO_JOIN | READONLY;
            }

        } catch (Exception e) {
            log.error("Error processing key change request: ", e);
            ctx.put("RESPONSE_CODE", "96");
            return PREPARED | NO_JOIN | READONLY;
        }
    }

    /**
     * Handle key request (operation codes 01/02).
     * Server generates new key and sends encrypted version to terminal.
     */
    private int handleKeyRequest(Context ctx, String terminalId, CryptoKey.KeyType keyType, ISOMsg request) {
        log.info("Processing key request: terminalId={}, keyType={}, STAN={}",
                terminalId, keyType, request.getString(11));

        try {
            // Request key distribution from HSM via KeyRotationService
            KeyRotationResponse rotationResponse = getKeyRotationService().requestKeyDistribution(
                    terminalId,
                    keyType
            );

            // Store rotation info in context for response building
            ctx.put("KEY_CHANGE_ROTATION_ID", rotationResponse.getRotationId());
            ctx.put("KEY_CHANGE_TYPE", keyType);
            ctx.put("KEY_CHANGE_ENCRYPTED_KEY", rotationResponse.getEncryptedNewKey());
            ctx.put("KEY_CHANGE_CHECKSUM", rotationResponse.getNewKeyChecksum());
            ctx.put("KEY_CHANGE_TERMINAL_ID", terminalId);
            ctx.put("RESPONSE_CODE", "00");

            log.info("Key distribution prepared successfully: rotationId={}, terminalId={}, keyType={}",
                    rotationResponse.getRotationId(), terminalId, keyType);

            return PREPARED | NO_JOIN | READONLY;

        } catch (Exception e) {
            log.error("Failed to process key request: {}", e.getMessage(), e);
            ctx.put("RESPONSE_CODE", "96");
            return PREPARED | NO_JOIN | READONLY;
        }
    }

    /**
     * Handle key confirmation (operation codes 03/04).
     * Terminal confirms successful installation of new key.
     * Server activates PENDING key and confirms to HSM.
     */
    private int handleKeyConfirmation(Context ctx, String terminalId, CryptoKey.KeyType keyType, ISOMsg request) {
        log.info("Processing key confirmation: terminalId={}, keyType={}, STAN={}",
                terminalId, keyType, request.getString(11));

        try {
            // Mark for activation in KeyActivationParticipant
            ctx.put("KEY_CHANGE_CONFIRMED", true);
            ctx.put("KEY_CHANGE_TYPE", keyType);
            ctx.put("KEY_CHANGE_TERMINAL_ID", terminalId);
            ctx.put("RESPONSE_CODE", "00");

            log.info("Key confirmation received: terminalId={}, keyType={}", terminalId, keyType);

            return PREPARED | NO_JOIN | READONLY;

        } catch (Exception e) {
            log.error("Failed to process key confirmation: {}", e.getMessage(), e);
            ctx.put("RESPONSE_CODE", "96");
            return PREPARED | NO_JOIN | READONLY;
        }
    }

    /**
     * Handle key installation failure (operation codes 05/06).
     * Terminal reports that key installation failed.
     * Server removes PENDING key and notifies HSM.
     */
    private int handleKeyFailure(Context ctx, String terminalId, CryptoKey.KeyType keyType, ISOMsg request) {
        log.error("KEY INSTALLATION FAILED: terminalId={}, keyType={}, STAN={}",
                terminalId, keyType, request.getString(11));

        try {
            // Extract failure reason from field 48 if present
            String failureReason = request.getString(48);
            if (failureReason != null && !failureReason.trim().isEmpty()) {
                log.error("Failure reason: {}", failureReason);
            }

            // Mark failure in context for KeyFailureParticipant to handle cleanup in commit phase
            ctx.put("KEY_CHANGE_FAILED", true);
            ctx.put("KEY_CHANGE_TYPE", keyType);
            ctx.put("KEY_CHANGE_TERMINAL_ID", terminalId);
            ctx.put("KEY_CHANGE_FAILURE_REASON", failureReason);
            ctx.put("RESPONSE_CODE", "00");  // Acknowledge receipt of failure notification

            log.warn("Key installation failure acknowledged: terminalId={}, keyType={}, reason={}",
                    terminalId, keyType, failureReason);

            return PREPARED | NO_JOIN | READONLY;

        } catch (Exception e) {
            log.error("Failed to process key failure notification: {}", e.getMessage(), e);
            ctx.put("RESPONSE_CODE", "96");
            return PREPARED | NO_JOIN | READONLY;
        }
    }

    @Override
    public void commit(long id, Serializable context) {
    }

    @Override
    public void abort(long id, Serializable context) {
    }
}
