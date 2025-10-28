package com.example.atm.participants;

import org.jpos.core.Configurable;
import org.jpos.core.Configuration;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.transaction.Context;
import org.jpos.transaction.TransactionParticipant;
import org.jpos.util.Log;
import org.jpos.util.Logger;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Random;

public class BalanceInquiry implements TransactionParticipant, Configurable {
    private Configuration cfg;
    private Logger logger;
    private Random random = new Random();

    @Override
    public int prepare(long id, Serializable context) {
        Context ctx = (Context) context;
        String mti = (String) ctx.get("MTI");
        String processingCode = (String) ctx.get("PROCESSING_CODE");

        if (!"0200".equals(mti)) {
            return PREPARED | NO_JOIN;
        }

        if (processingCode == null || !processingCode.startsWith("31")) {
            return PREPARED | NO_JOIN;
        }

        String pan = (String) ctx.get("PAN");
        String terminalId = (String) ctx.get("TERMINAL_ID");

        info("Balance inquiry for PAN: " + pan + " from terminal: " + terminalId);

        BigDecimal balance = simulateBalanceInquiry(pan);
        ctx.put("BALANCE", balance);
        ctx.put("RESPONSE_CODE", "00");
        ctx.put("TRANSACTION_TYPE", "BALANCE_INQUIRY");

        info("Balance inquiry successful. Balance: " + balance);

        return PREPARED | NO_JOIN;
    }

    private BigDecimal simulateBalanceInquiry(String pan) {
        int balanceAmount = 1000000 + random.nextInt(9000000);
        return new BigDecimal(balanceAmount);
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
            org.jpos.util.LogEvent evt = new org.jpos.util.LogEvent("balance-inquiry");
            evt.addMessage(msg);
            logger.log(evt);
        }
    }
}
