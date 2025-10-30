package com.example.atm.jpos.participant;

import java.io.Serializable;

import org.jpos.iso.ISOMsg;
import org.jpos.transaction.Context;
import org.jpos.transaction.TransactionParticipant;

import com.example.atm.config.HsmProperties;
import com.example.atm.entity.PinEncryptionAlgorithm;
import com.example.atm.jpos.SpringBeanFactory;
import com.example.atm.service.BankService;

import lombok.extern.slf4j.Slf4j;

/**
 * jPOS TransactionParticipant for PIN verification.
 * Supports AES-128 and AES-256 PIN blocks in field 123 (binary field, 32 bytes).
 * PIN verification is MANDATORY - transactions without PIN blocks will be rejected (response code 55).
 * This keeps jPOS layer minimal - business logic stays in service layer.
 * Note: This class is NOT managed by Spring - it's instantiated by jPOS Q2.
 */
@Slf4j
public class PinVerificationParticipant implements TransactionParticipant {

    private BankService getBankService() {
        return SpringBeanFactory.getBean(BankService.class);
    }

    private HsmProperties getHsmProperties() {
        return SpringBeanFactory.getBean(HsmProperties.class);
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

            // Skip PIN verification if an error response code is already set (e.g., account not found)
            String existingResponseCode = (String) ctx.get("RESPONSE_CODE");
            if (existingResponseCode != null && !"00".equals(existingResponseCode)) {
                log.debug("Skipping PIN verification - error response code already set: {}", existingResponseCode);
                return PREPARED | NO_JOIN | READONLY;
            }

            // Determine PIN encryption algorithm from configuration
            PinEncryptionAlgorithm algorithm = getHsmProperties().getPin().getEncryptionAlgorithm();
            int pinField = algorithm.getIsoField();
            log.debug("Pin Algorithm : {}", algorithm);

            // Extract PIN block from field 123 (AES only)
            // Field 123: binary field, 32 bytes (IV + ciphertext)
            String pinBlock = null;
            byte[] pinBlockBytes = msg.getBytes(123);
            if (pinBlockBytes != null && pinBlockBytes.length > 0) {
                pinBlock = bytesToHex(pinBlockBytes);
                log.debug("Found {} PIN block in field 123: {} bytes", algorithm.getDisplayName(), pinBlockBytes.length);
            }

            if (pinBlock == null || pinBlock.isEmpty()) {
                log.error("PIN block is required but not found in field 123");
                ctx.put("RESPONSE_CODE", "55"); // Incorrect PIN / PIN required
                return PREPARED | NO_JOIN | READONLY;
            }

            String pan = msg.getString(2);
            String accountNumber = msg.getString(102);

            if (accountNumber == null || accountNumber.isEmpty()) {
                log.error("Account number not found in field 102");
                ctx.put("RESPONSE_CODE", "30");
                return PREPARED | NO_JOIN | READONLY;
            }

            // Build full terminal ID from field 42 (institution) + field 41 (terminal)
            String cardAcceptorId = msg.getString(42);  // e.g., "TRM-ISS001"
            String terminalId = msg.getString(41);       // e.g., "ATM-001"
            String fullTerminalId = (cardAcceptorId != null && !cardAcceptorId.isEmpty())
                    ? cardAcceptorId.trim() + "-" + terminalId.trim()
                    : terminalId.trim();

            log.info("PIN verification requested for account: {} using {} terminal: {}",
                    accountNumber, algorithm.getDisplayName(), fullTerminalId);

            // Store algorithm in context for service layer
            ctx.put("PIN_ALGORITHM", algorithm);

            getBankService().verifyPin(accountNumber, pinBlock, pan, fullTerminalId);

            log.info("PIN verification successful for account: {} using {}", accountNumber, algorithm.getDisplayName());
            ctx.put("PIN_VERIFIED", true);
            return PREPARED | NO_JOIN | READONLY;

        } catch (Exception e) {
            log.error("PIN verification failed: {}", e.getMessage());
            ctx.put("RESPONSE_CODE", "55");
            return PREPARED | NO_JOIN | READONLY;
        }
    }

    @Override
    public void commit(long id, Serializable context) {
    }

    @Override
    public void abort(long id, Serializable context) {
        log.debug("PIN verification participant aborted for transaction: {}", id);
    }

    /**
     * Convert byte array to hex string.
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02X", b));
        }
        return result.toString();
    }
}
