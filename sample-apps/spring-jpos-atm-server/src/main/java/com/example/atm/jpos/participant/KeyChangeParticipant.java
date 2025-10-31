package com.example.atm.jpos.participant;

import com.example.atm.dto.rotation.KeyRotationResponse;
import com.example.atm.entity.CryptoKey;
import com.example.atm.jpos.SpringBeanFactory;
import com.example.atm.service.KeyRotationService;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOMsg;
import org.jpos.transaction.Context;
import org.jpos.transaction.TransactionParticipant;

import java.io.Serializable;

/**
 * jPOS TransactionParticipant for handling terminal-initiated key change requests.
 * Processes MTI 0800 network management messages for key rotation.
 *
 * Field usage:
 * - Field 11: STAN (System Trace Audit Number)
 * - Field 53: Security Related Control Information
 *   Format: first 2 digits indicate operation
 *   01 = TPK change request
 *   02 = TSK change request
 * - Field 123 (response): Encrypted new key (KTK encrypted under current key)
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

            String securityControl = request.getString(53);
            if (securityControl == null || securityControl.length() < 2) {
                log.error("Invalid or missing security control information in field 53");
                ctx.put("RESPONSE_CODE", "30");
                return PREPARED | NO_JOIN | READONLY;
            }

            String operationCode = securityControl.substring(0, 2);
            CryptoKey.KeyType keyType;

            switch (operationCode) {
                case "01":
                    keyType = CryptoKey.KeyType.TPK;
                    log.info("TPK key change request received");
                    break;
                case "02":
                    keyType = CryptoKey.KeyType.TSK;
                    log.info("TSK key change request received");
                    break;
                default:
                    log.error("Unsupported security operation: {}", operationCode);
                    ctx.put("RESPONSE_CODE", "30");
                    return PREPARED | NO_JOIN | READONLY;
            }

            // Extract terminal ID from field 41 (Card Acceptor Terminal ID)
            String terminalId = request.getString(41);
            if (terminalId == null || terminalId.trim().isEmpty()) {
                log.error("Terminal ID not found in field 41");
                ctx.put("RESPONSE_CODE", "30");
                return PREPARED | NO_JOIN | READONLY;
            }
            terminalId = terminalId.trim();

            log.info("Processing key change request: terminalId={}, keyType={}, STAN={}",
                    terminalId, keyType, request.getString(11));

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
            ctx.put("RESPONSE_CODE", "00");

            log.info("Key distribution prepared successfully: rotationId={}, terminalId={}, keyType={}",
                    rotationResponse.getRotationId(), terminalId, keyType);

            return PREPARED | NO_JOIN | READONLY;

        } catch (Exception e) {
            log.error("Error processing key change request: ", e);
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
