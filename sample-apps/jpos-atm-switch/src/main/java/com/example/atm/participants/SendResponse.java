package com.example.atm.participants;

import org.jpos.core.Configurable;
import org.jpos.core.Configuration;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOSource;
import org.jpos.transaction.Context;
import org.jpos.transaction.TransactionParticipant;
import org.jpos.util.Logger;

import java.io.Serializable;
import java.math.BigDecimal;

public class SendResponse implements TransactionParticipant, Configurable {
    private Configuration cfg;
    private Logger logger;

    @Override
    public int prepare(long id, Serializable context) {
        Context ctx = (Context) context;
        ISOMsg request = (ISOMsg) ctx.get("REQUEST");
        ISOSource source = (ISOSource) ctx.get("SOURCE");

        if (request == null || source == null) {
            return ABORTED | NO_JOIN;
        }

        try {
            ISOMsg response = (ISOMsg) request.clone();
            String mti = request.getMTI();

            if ("0200".equals(mti)) {
                response.setMTI("0210");
            } else if ("0400".equals(mti)) {
                response.setMTI("0410");
            }

            String responseCode = (String) ctx.get("RESPONSE_CODE");
            if (responseCode == null) {
                responseCode = "96";
            }
            response.set(39, responseCode);

            String transactionType = (String) ctx.get("TRANSACTION_TYPE");
            if ("BALANCE_INQUIRY".equals(transactionType)) {
                BigDecimal balance = (BigDecimal) ctx.get("BALANCE");
                if (balance != null) {
                    response.set(54, balance.toString());
                }
            } else if ("CASH_WITHDRAWAL".equals(transactionType)) {
                BigDecimal newBalance = (BigDecimal) ctx.get("NEW_BALANCE");
                if (newBalance != null) {
                    response.set(54, newBalance.toString());
                }
            }

            source.send(response);
            ctx.put("RESPONSE", response);
            info("Response sent with code: " + responseCode);

        } catch (ISOException e) {
            ctx.put("ERROR", e.getMessage());
            return ABORTED | NO_JOIN;
        } catch (Exception e) {
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
        Context ctx = (Context) context;
        ISOMsg request = (ISOMsg) ctx.get("REQUEST");
        ISOSource source = (ISOSource) ctx.get("SOURCE");

        if (request == null || source == null) {
            return;
        }

        try {
            ISOMsg response = (ISOMsg) request.clone();
            String mti = request.getMTI();

            if ("0200".equals(mti)) {
                response.setMTI("0210");
            } else if ("0400".equals(mti)) {
                response.setMTI("0410");
            }

            response.set(39, "96");
            source.send(response);
            info("Error response sent with code: 96");

        } catch (Exception e) {
            info("Failed to send error response: " + e.getMessage());
        }
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
            org.jpos.util.LogEvent evt = new org.jpos.util.LogEvent("send-response");
            evt.addMessage(msg);
            logger.log(evt);
        }
    }
}
