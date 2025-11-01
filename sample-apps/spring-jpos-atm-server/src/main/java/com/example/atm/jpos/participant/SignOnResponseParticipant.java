package com.example.atm.jpos.participant;

import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.transaction.Context;
import org.jpos.transaction.TransactionParticipant;

import java.io.Serializable;

/**
 * Participant to handle sign-on/sign-off messages and send responses.
 * Processes MTI 0800 with field 70 (network management information code).
 *
 * Field 70 values:
 * - "001" = Sign-on request
 * - "002" = Sign-off request
 *
 * Sends 0810 response with:
 * - Field 39 = "00" (approved)
 * - Echoed fields: 11 (STAN), 41 (Terminal ID), 42 (Institution ID), 70 (Network Mgmt Code)
 *
 * Note: This class is NOT managed by Spring - it's instantiated by jPOS Q2.
 */
@Slf4j
public class SignOnResponseParticipant implements TransactionParticipant {

    @Override
    public int prepare(long id, Serializable context) {
        Context ctx = (Context) context;

        try {
            ISOMsg request = (ISOMsg) ctx.get("REQUEST");

            if (request == null) {
                return PREPARED | NO_JOIN | READONLY;
            }

            String mti = request.getMTI();

            // Only process network management messages (0800)
            if (!"0800".equals(mti)) {
                return PREPARED | NO_JOIN | READONLY;
            }

            // Check if this is a sign-on/sign-off message (field 70)
            String networkMgmtCode = request.getString(70);
            if (networkMgmtCode == null || networkMgmtCode.isEmpty()) {
                // Not a sign-on/sign-off message, let other participants handle it
                return PREPARED | NO_JOIN | READONLY;
            }

            // Process sign-on (001) or sign-off (002)
            if ("001".equals(networkMgmtCode)) {
                log.info("Processing sign-on request: terminalId={}",
                    extractTerminalId(request));
                buildSignOnResponse(ctx, request);
                return PREPARED | NO_JOIN | READONLY;
            } else if ("002".equals(networkMgmtCode)) {
                log.info("Processing sign-off request: terminalId={}",
                    extractTerminalId(request));
                buildSignOffResponse(ctx, request);
                return PREPARED | NO_JOIN | READONLY;
            }

            // Other network management codes, let other participants handle
            return PREPARED | NO_JOIN | READONLY;

        } catch (Exception e) {
            log.error("Error processing sign-on/sign-off message: {}", e.getMessage(), e);
            ctx.put("RESPONSE_CODE", "96"); // System error
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
     * Build sign-on response (0810).
     */
    private void buildSignOnResponse(Context ctx, ISOMsg request) throws ISOException {
        ISOMsg response = (ISOMsg) request.clone();
        response.setMTI("0810");

        // Field 39: Response code = "00" (approved)
        response.set(39, "00");

        // Echo fields from request
        if (request.hasField(11)) {
            response.set(11, request.getString(11)); // STAN
        }
        if (request.hasField(41)) {
            response.set(41, request.getString(41)); // Terminal ID
        }
        if (request.hasField(42)) {
            response.set(42, request.getString(42)); // Institution ID
        }
        if (request.hasField(70)) {
            response.set(70, request.getString(70)); // Network management code
        }

        ctx.put("RESPONSE", response);
        ctx.put("RESPONSE_CODE", "00");

        log.debug("Built sign-on response: MTI={}, responseCode=00", response.getMTI());
    }

    /**
     * Build sign-off response (0810).
     */
    private void buildSignOffResponse(Context ctx, ISOMsg request) throws ISOException {
        ISOMsg response = (ISOMsg) request.clone();
        response.setMTI("0810");

        // Field 39: Response code = "00" (approved)
        response.set(39, "00");

        // Echo fields from request
        if (request.hasField(11)) {
            response.set(11, request.getString(11)); // STAN
        }
        if (request.hasField(41)) {
            response.set(41, request.getString(41)); // Terminal ID
        }
        if (request.hasField(42)) {
            response.set(42, request.getString(42)); // Institution ID
        }
        if (request.hasField(70)) {
            response.set(70, request.getString(70)); // Network management code
        }

        ctx.put("RESPONSE", response);
        ctx.put("RESPONSE_CODE", "00");

        log.debug("Built sign-off response: MTI={}, responseCode=00", response.getMTI());
    }

    /**
     * Extract terminal ID from ISO message for logging.
     */
    private String extractTerminalId(ISOMsg msg) {
        try {
            String cardAcceptorId = msg.getString(42);
            String terminalId = msg.getString(41);

            if (terminalId == null || terminalId.trim().isEmpty()) {
                return "UNKNOWN";
            }

            if (cardAcceptorId != null && !cardAcceptorId.trim().isEmpty()) {
                return cardAcceptorId.trim() + "-" + terminalId.trim();
            }

            return terminalId.trim();

        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
}
