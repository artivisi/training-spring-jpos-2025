package com.example.atm.jpos.participant;

import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.transaction.Context;
import org.jpos.transaction.TransactionParticipant;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * jPOS TransactionParticipant for building ISO-8583 response messages.
 * Note: This class is NOT managed by Spring - it's instantiated by jPOS Q2.
 */
@Slf4j
public class ResponseBuilderParticipant implements TransactionParticipant {

    @Override
    public int prepare(long id, Serializable context) {
        Context ctx = (Context) context;
        try {
            // Check if response already built by another participant (e.g., SignOnResponseParticipant)
            ISOMsg existingResponse = (ISOMsg) ctx.get("RESPONSE");
            if (existingResponse != null) {
                log.debug("Response already built by another participant, skipping");
                return PREPARED | NO_JOIN | READONLY;
            }

            ISOMsg request = (ISOMsg) ctx.get("REQUEST");
            String responseCode = (String) ctx.get("RESPONSE_CODE");

            if (request == null) {
                log.error("No request message in context");
                // Still try to continue - don't abort
                responseCode = "96";
            }

            if (responseCode == null) {
                responseCode = "00"; // Success if no error set
            }

            if (request != null) {
                ISOMsg response = (ISOMsg) request.clone();
                response.setDirection(ISOMsg.OUTGOING);

                String mti = request.getMTI();
                String responseMTI = mti.substring(0, 2) + "10";
                response.setMTI(responseMTI);

                response.set(39, responseCode);

                // Handle key change response (MTI 0810)
                if ("0810".equals(responseMTI)) {
                    String encryptedKey = (String) ctx.get("KEY_CHANGE_ENCRYPTED_KEY");
                    String keyChecksum = (String) ctx.get("KEY_CHANGE_CHECKSUM");

                    if (encryptedKey != null) {
                        // Field 123: Encrypted new key from HSM
                        response.set(123, encryptedKey);
                        log.debug("Added encrypted key to field 123");
                    }

                    if (keyChecksum != null) {
                        // Field 48: Key checksum for verification
                        response.set(48, keyChecksum);
                        log.debug("Added key checksum to field 48");
                    }
                }

                // Handle financial transaction response fields
                BigDecimal balance = (BigDecimal) ctx.get("BALANCE");
                if (balance != null) {
                    String balanceStr = String.format("%012d", balance.multiply(new BigDecimal("100")).longValue());
                    response.set(54, "001360" + balanceStr);
                }

                String referenceNumber = (String) ctx.get("REFERENCE_NUMBER");
                if (referenceNumber != null) {
                    String rrn = referenceNumber.length() > 12
                        ? referenceNumber.substring(0, 12)
                        : referenceNumber;
                    response.set(37, rrn);
                }

                ctx.put("RESPONSE", response);

                log.info("Response built with MTI: {} Response Code: {}", responseMTI, responseCode);
            }

            // Always return PREPARED so SendResponseParticipant can execute
            return PREPARED | NO_JOIN | READONLY;

        } catch (ISOException e) {
            log.error("Error building response: ", e);
            // Still return PREPARED to allow SendResponseParticipant to run
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
