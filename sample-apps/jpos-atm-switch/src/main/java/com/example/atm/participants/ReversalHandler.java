package com.example.atm.participants;

import org.jpos.core.Configurable;
import org.jpos.core.Configuration;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.transaction.Context;
import org.jpos.transaction.TransactionParticipant;
import org.jpos.util.Logger;

import java.io.Serializable;

public class ReversalHandler implements TransactionParticipant, Configurable {
    private Configuration cfg;
    private Logger logger;

    @Override
    public int prepare(long id, Serializable context) {
        Context ctx = (Context) context;
        ISOMsg msg = (ISOMsg) ctx.get("REQUEST");

        if (msg == null) {
            return PREPARED | NO_JOIN;
        }

        try {
            String mti = msg.getMTI();

            if ("0400".equals(mti)) {
                info("Reversal message received");
                String pan = (String) ctx.get("PAN");
                String stan = (String) ctx.get("STAN");
                String terminalId = (String) ctx.get("TERMINAL_ID");

                info("Processing reversal for PAN: " + pan + ", STAN: " + stan + ", Terminal: " + terminalId);

                ctx.put("TRANSACTION_TYPE", "REVERSAL");
                ctx.put("RESPONSE_CODE", "00");

                info("Reversal processed successfully");
            }

        } catch (ISOException e) {
            ctx.put("RESPONSE_CODE", "96");
            ctx.put("ERROR", e.getMessage());
            return PREPARED | NO_JOIN;
        }

        return PREPARED | NO_JOIN;
    }

    @Override
    public void commit(long id, Serializable context) {
    }

    @Override
    public void abort(long id, Serializable context) {
        Context ctx = (Context) context;
        ctx.put("RESPONSE_CODE", "96");
    }

    @Override
    public void setConfiguration(Configuration cfg) {
        this.cfg = cfg;
        String loggerName = cfg.get("logger");
        if (loggerName != null) {
            logger = Logger.getLogger(loggerName);
        }
    }

    private void info(String msg) {
        if (logger != null) {
            org.jpos.util.LogEvent evt = new org.jpos.util.LogEvent("reversal-handler");
            evt.addMessage(msg);
            logger.log(evt);
        }
    }
}
