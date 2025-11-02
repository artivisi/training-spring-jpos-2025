package com.example.atm.jpos.participant;

import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.transaction.Context;
import org.jpos.transaction.GroupSelector;

import java.io.Serializable;

/**
 * jPOS GroupSelector to route ISO-8583 messages to appropriate participant groups.
 * Routes based on MTI (Message Type Indicator) and specific fields.
 *
 * Routing Logic:
 * - MTI 0800 + Field 70 (Network Mgmt Code) → NetworkManagement group (sign-on/sign-off)
 * - MTI 0800 + Field 53 (Security Control) → KeyChange group (key rotation)
 * - MTI 0200 → FinancialTransaction group (balance inquiry, withdrawal)
 *
 * This enables:
 * - Reusable participants (e.g., MacVerificationParticipant in multiple groups)
 * - Clear separation of concerns
 * - Easy-to-read XML configuration
 *
 * Note: This class is NOT managed by Spring - it's instantiated by jPOS Q2.
 */
@Slf4j
public class MTISelector implements GroupSelector {

    /**
     * Select the appropriate participant group based on message type.
     * Called by TransactionManager during the prepare phase.
     *
     * @param id Transaction ID
     * @param context Transaction context containing REQUEST message
     * @return Space-separated group names to execute, or null if no group should run
     */
    @Override
    public String select(long id, Serializable context) {
        Context ctx = (Context) context;
        try {
            ISOMsg request = (ISOMsg) ctx.get("REQUEST");

            if (request == null) {
                log.error("No ISO message in context for transaction {}", id);
                return null;
            }

            String mti = request.getMTI();
            log.debug("MTI Selector: routing MTI={}", mti);

            // Route based on MTI
            switch (mti) {
                case "0800":
                    return selectNetworkManagementGroup(request);

                case "0200":
                    log.info("Routing to FinancialTransaction group: MTI={}", mti);
                    return "FinancialTransaction";

                default:
                    log.warn("Unsupported MTI: {}", mti);
                    ctx.put("RESPONSE_CODE", "30"); // Format error
                    return null;
            }

        } catch (Exception e) {
            log.error("Error in MTI selector for transaction {}: {}", id, e.getMessage(), e);
            ctx.put("RESPONSE_CODE", "96"); // System error
            return null;
        }
    }

    /**
     * Select appropriate group for network management messages (0800).
     * Determines routing based on presence of specific fields.
     *
     * @param request The ISO-8583 message
     * @return Group name or null
     */
    private String selectNetworkManagementGroup(ISOMsg request) throws ISOException {
        // Check for network management code (sign-on/sign-off)
        if (request.hasField(70)) {
            String networkMgmtCode = request.getString(70);
            log.info("Routing to NetworkManagement group: MTI=0800, field70={}", networkMgmtCode);
            return "NetworkManagement";
        }

        // Check for security control (key change)
        if (request.hasField(53)) {
            String securityControl = request.getString(53);
            log.info("Routing to KeyChange group: MTI=0800, field53={}", securityControl);
            return "KeyChange";
        }

        // 0800 without field 70 or 53 - unsupported
        log.warn("Unsupported 0800 message: no field 70 (network mgmt) or field 53 (key change)");
        return null;
    }

    /**
     * Called during prepare phase before select().
     * This selector has no state to prepare, so always returns PREPARED.
     *
     * @param id Transaction ID
     * @param context Transaction context
     * @return PREPARED flag
     */
    @Override
    public int prepare(long id, Serializable context) {
        return PREPARED | READONLY;
    }

    /**
     * Called during commit phase.
     * This selector has no commit logic.
     */
    @Override
    public void commit(long id, Serializable context) {
        // No commit logic needed for selector
    }

    /**
     * Called during abort phase.
     * This selector has no abort logic.
     */
    @Override
    public void abort(long id, Serializable context) {
        // No abort logic needed for selector
    }
}
