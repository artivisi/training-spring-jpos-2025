package com.example.atm.jpos.participant;

import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOSource;
import org.jpos.transaction.Context;
import org.jpos.transaction.TransactionParticipant;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Component
@Slf4j
public class SendResponseParticipant implements TransactionParticipant {

    @Override
    public int prepare(long id, Serializable context) {
        return PREPARED | READONLY;
    }

    @Override
    public void commit(long id, Serializable context) {
        Context ctx = (Context) context;
        try {
            ISOSource source = (ISOSource) ctx.get("SOURCE");
            ISOMsg response = (ISOMsg) ctx.get("RESPONSE");

            log.info("SendResponseParticipant.commit called - source: {}, response: {}",
                     source != null, response != null);

            if (source != null && response != null) {
                log.info("Sending response: MTI={} RC={}",
                         response.getMTI(), response.getString(39));
                source.send(response);
                log.info("Response sent successfully");
            } else {
                log.error("Missing SOURCE or RESPONSE in context - source: {}, response: {}",
                         source != null, response != null);
            }

        } catch (Exception e) {
            log.error("Error sending response: ", e);
        }
    }

    @Override
    public void abort(long id, Serializable context) {
        log.warn("Transaction {} aborted", id);
    }
}
