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
 * Supports both legacy 3DES (field 52) and modern AES-128 (field 123) PIN blocks.
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
                return ABORTED;
            }

            // Determine PIN encryption algorithm from configuration
            PinEncryptionAlgorithm algorithm = getHsmProperties().getPin().getEncryptionAlgorithm();
            int pinField = algorithm.getIsoField();

            // Extract PIN block from appropriate field based on algorithm
            String pinBlock = null;
            if (algorithm == PinEncryptionAlgorithm.TDES) {
                // Legacy 3DES: field 52 (8 bytes)
                pinBlock = msg.getString(52);
                log.debug("Checking for 3DES PIN block in field 52");
            } else if (algorithm == PinEncryptionAlgorithm.AES_128) {
                // Modern AES-128: field 123 (16 bytes as hex string)
                pinBlock = msg.getString(123);
                log.debug("Checking for AES-128 PIN block in field 123");
            }

            // If no PIN block found in configured field, check alternative field for backward compatibility
            if (pinBlock == null || pinBlock.isEmpty()) {
                if (algorithm == PinEncryptionAlgorithm.AES_128 && msg.hasField(52)) {
                    log.debug("No AES PIN in field 123, checking field 52 for 3DES fallback");
                    pinBlock = msg.getString(52);
                    algorithm = PinEncryptionAlgorithm.TDES;
                } else if (algorithm == PinEncryptionAlgorithm.TDES && msg.hasField(123)) {
                    log.debug("No 3DES PIN in field 52, checking field 123 for AES");
                    pinBlock = msg.getString(123);
                    algorithm = PinEncryptionAlgorithm.AES_128;
                }
            }

            if (pinBlock == null || pinBlock.isEmpty()) {
                log.debug("No PIN block found in fields 52 or 123, skipping PIN verification");
                return PREPARED | NO_JOIN | READONLY;
            }

            String pan = msg.getString(2);
            String accountNumber = msg.getString(102);

            if (accountNumber == null || accountNumber.isEmpty()) {
                log.error("Account number not found in field 102");
                ctx.put("RESPONSE_CODE", "30");
                return ABORTED;
            }

            log.info("PIN verification requested for account: {} using {}", accountNumber, algorithm.getDisplayName());

            // Store algorithm in context for service layer
            ctx.put("PIN_ALGORITHM", algorithm);

            getBankService().verifyPin(accountNumber, pinBlock, pan);

            log.info("PIN verification successful for account: {} using {}", accountNumber, algorithm.getDisplayName());
            ctx.put("PIN_VERIFIED", true);
            return PREPARED | NO_JOIN | READONLY;

        } catch (Exception e) {
            log.error("PIN verification failed: {}", e.getMessage());
            ctx.put("RESPONSE_CODE", "55");
            return ABORTED;
        }
    }

    @Override
    public void commit(long id, Serializable context) {
    }

    @Override
    public void abort(long id, Serializable context) {
        log.debug("PIN verification participant aborted for transaction: {}", id);
    }
}
