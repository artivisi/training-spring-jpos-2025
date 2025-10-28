package com.example.atm.participants;

import org.jpos.core.Configurable;
import org.jpos.core.Configuration;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.transaction.Context;
import org.jpos.transaction.TransactionParticipant;

import java.io.Serializable;

public class CheckMandatoryFields implements TransactionParticipant, Configurable {
    private Configuration cfg;

    @Override
    public int prepare(long id, Serializable context) {
        Context ctx = (Context) context;
        ISOMsg msg = (ISOMsg) ctx.get("REQUEST");

        if (msg == null) {
            ctx.put("RESULT", "NO_REQUEST");
            return ABORTED | NO_JOIN;
        }

        try {
            String mti = msg.getMTI();
            if (mti == null) {
                ctx.put("RESULT", "INVALID_MTI");
                return ABORTED | NO_JOIN;
            }

            if (!msg.hasField(2)) {
                ctx.put("RESULT", "MISSING_PAN");
                return ABORTED | NO_JOIN;
            }

            if (!msg.hasField(3)) {
                ctx.put("RESULT", "MISSING_PROCESSING_CODE");
                return ABORTED | NO_JOIN;
            }

            if (!msg.hasField(11)) {
                ctx.put("RESULT", "MISSING_STAN");
                return ABORTED | NO_JOIN;
            }

            if (!msg.hasField(41)) {
                ctx.put("RESULT", "MISSING_CARD_ACCEPTOR_TERMINAL_ID");
                return ABORTED | NO_JOIN;
            }

            ctx.put("MTI", mti);
            ctx.put("PAN", msg.getString(2));
            ctx.put("PROCESSING_CODE", msg.getString(3));
            ctx.put("STAN", msg.getString(11));
            ctx.put("TERMINAL_ID", msg.getString(41));

        } catch (ISOException e) {
            ctx.put("RESULT", "ERROR_PARSING_MESSAGE");
            ctx.put("ERROR", e.getMessage());
            return ABORTED | NO_JOIN;
        }

        return PREPARED | NO_JOIN;
    }

    @Override
    public void commit(long id, Serializable context) {
    }

    @Override
    public void abort(long id, Serializable context) {
    }

    @Override
    public void setConfiguration(Configuration cfg) {
        this.cfg = cfg;
    }
}
