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
            ISOMsg request = (ISOMsg) ctx.get("REQUEST");
            String responseCode = (String) ctx.get("RESPONSE_CODE");

            if (request == null) {
                log.error("No request message in context");
                return ABORTED;
            }

            if (responseCode == null) {
                responseCode = "96";
            }

            ISOMsg response = (ISOMsg) request.clone();
            response.setDirection(ISOMsg.OUTGOING);

            String mti = request.getMTI();
            String responseMTI = mti.substring(0, 2) + "10";
            response.setMTI(responseMTI);

            response.set(39, responseCode);

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

            return PREPARED | NO_JOIN;

        } catch (ISOException e) {
            log.error("Error building response: ", e);
            return ABORTED;
        }
    }

    @Override
    public void commit(long id, Serializable context) {
    }

    @Override
    public void abort(long id, Serializable context) {
    }
}
