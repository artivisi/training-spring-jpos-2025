package com.example.atm.jpos.participant;

import java.io.Serializable;

import org.jpos.iso.ISOMsg;
import org.jpos.transaction.Context;
import org.jpos.transaction.TransactionParticipant;

import com.example.atm.jpos.SpringBeanFactory;
import com.example.atm.service.BankService;

import lombok.extern.slf4j.Slf4j;

/**
 * jPOS TransactionParticipant for PIN verification.
 * Extracts PIN block from ISO field 52 and delegates verification to BankService.
 * This keeps jPOS layer minimal - business logic stays in service layer.
 * Note: This class is NOT managed by Spring - it's instantiated by jPOS Q2.
 */
@Slf4j
public class PinVerificationParticipant implements TransactionParticipant {

    private BankService getBankService() {
        return SpringBeanFactory.getBean(BankService.class);
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

            String pinBlock = msg.getString(52);
            if (pinBlock == null || pinBlock.isEmpty()) {
                log.debug("No PIN block in field 52, skipping PIN verification");
                return PREPARED | NO_JOIN | READONLY;
            }

            String pan = msg.getString(2);
            String accountNumber = msg.getString(102);

            if (accountNumber == null || accountNumber.isEmpty()) {
                log.error("Account number not found in field 102");
                ctx.put("RESPONSE_CODE", "30");
                return ABORTED;
            }

            log.debug("PIN verification requested for account: {}", accountNumber);

            getBankService().verifyPin(accountNumber, pinBlock, pan);

            log.info("PIN verification successful for account: {}", accountNumber);
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
