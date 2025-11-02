package com.artivisi.atm.jpos.participant;

import com.artivisi.atm.jpos.SpringBeanFactory;
import com.artivisi.atm.jpos.service.ChannelRegistry;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOMsg;
import org.jpos.transaction.Context;
import org.jpos.transaction.TransactionParticipant;

import java.io.Serializable;

/**
 * jPOS TransactionParticipant to enforce sign-on requirement.
 * Rejects all transactions if terminal has not completed sign-on (0800/001).
 *
 * Exceptions:
 * - MTI 0800 messages (network management) are always allowed
 * - This allows sign-on, echo test, and key change requests
 *
 * Note: This class is NOT managed by Spring - it's instantiated by jPOS Q2.
 */
@Slf4j
public class SignOnValidationParticipant implements TransactionParticipant {

    private ChannelRegistry getChannelRegistry() {
        return SpringBeanFactory.getBean(ChannelRegistry.class);
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

            // Always allow network management messages (0800)
            // This includes sign-on, sign-off, echo test, key change
            if ("0800".equals(mti)) {
                log.debug("Network management message, skipping sign-on validation: MTI={}", mti);
                return PREPARED | NO_JOIN | READONLY;
            }

            // Extract terminal ID
            String terminalId = extractTerminalId(request);
            if (terminalId == null || terminalId.trim().isEmpty()) {
                log.error("Cannot validate sign-on: missing terminal ID");
                ctx.put("RESPONSE_CODE", "96");
                return PREPARED | NO_JOIN | READONLY;
            }

            // Check if terminal is signed on
            if (!getChannelRegistry().isSignedOn(terminalId)) {
                log.error("Terminal not signed on, rejecting transaction: terminalId={}, MTI={}",
                        terminalId, mti);
                ctx.put("RESPONSE_CODE", "91"); // Issuer or switch inoperative
                return PREPARED | NO_JOIN | READONLY;
            }

            log.debug("Sign-on validation passed: terminalId={}, MTI={}", terminalId, mti);
            return PREPARED | NO_JOIN | READONLY;

        } catch (Exception e) {
            log.error("Sign-on validation error: {}", e.getMessage(), e);
            ctx.put("RESPONSE_CODE", "96");
            return PREPARED | NO_JOIN | READONLY;
        }
    }

    @Override
    public void commit(long id, Serializable context) {
        // No action needed
    }

    @Override
    public void abort(long id, Serializable context) {
        // No action needed
    }

    /**
     * Extract terminal ID from ISO message.
     * Combines field 42 (institution) + field 41 (terminal).
     */
    private String extractTerminalId(ISOMsg msg) {
        try {
            String cardAcceptorId = msg.getString(42);  // e.g., "TRM-ISS001"
            String terminalId = msg.getString(41);       // e.g., "ATM-001"

            if (terminalId == null || terminalId.trim().isEmpty()) {
                return null;
            }

            if (cardAcceptorId != null && !cardAcceptorId.trim().isEmpty()) {
                return cardAcceptorId.trim() + "-" + terminalId.trim();
            }

            return terminalId.trim();

        } catch (Exception e) {
            log.debug("Could not extract terminal ID: {}", e.getMessage());
            return null;
        }
    }
}
