package com.example.atm.participants;

import org.jpos.core.Configurable;
import org.jpos.core.Configuration;
import org.jpos.iso.ISOMsg;
import org.jpos.transaction.Context;
import org.jpos.transaction.TransactionParticipant;
import org.jpos.util.Logger;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class CashWithdrawal implements TransactionParticipant, Configurable {
    private Configuration cfg;
    private Logger logger;
    private Random random = new Random();
    private static Map<String, BigDecimal> accountBalances = new HashMap<>();

    @Override
    public int prepare(long id, Serializable context) {
        Context ctx = (Context) context;
        String mti = (String) ctx.get("MTI");
        String processingCode = (String) ctx.get("PROCESSING_CODE");

        if (!"0200".equals(mti)) {
            return PREPARED | NO_JOIN;
        }

        if (processingCode == null || !processingCode.startsWith("01")) {
            return PREPARED | NO_JOIN;
        }

        ISOMsg msg = (ISOMsg) ctx.get("REQUEST");
        String pan = (String) ctx.get("PAN");
        String terminalId = (String) ctx.get("TERMINAL_ID");

        try {
            if (!msg.hasField(4)) {
                ctx.put("RESPONSE_CODE", "30");
                return PREPARED | NO_JOIN;
            }

            String amountStr = msg.getString(4);
            BigDecimal withdrawalAmount = new BigDecimal(amountStr).divide(new BigDecimal("100"));

            info("Cash withdrawal request for PAN: " + pan + ", Amount: " + withdrawalAmount + " from terminal: " + terminalId);

            BigDecimal currentBalance = getOrCreateBalance(pan);

            if (currentBalance.compareTo(withdrawalAmount) < 0) {
                ctx.put("RESPONSE_CODE", "51");
                info("Insufficient funds. Current balance: " + currentBalance + ", Requested: " + withdrawalAmount);
                return PREPARED | NO_JOIN;
            }

            BigDecimal newBalance = currentBalance.subtract(withdrawalAmount);
            accountBalances.put(pan, newBalance);

            ctx.put("WITHDRAWAL_AMOUNT", withdrawalAmount);
            ctx.put("NEW_BALANCE", newBalance);
            ctx.put("RESPONSE_CODE", "00");
            ctx.put("TRANSACTION_TYPE", "CASH_WITHDRAWAL");
            ctx.put("ORIGINAL_AMOUNT", amountStr);

            info("Cash withdrawal successful. New balance: " + newBalance);

            return PREPARED;

        } catch (NumberFormatException e) {
            ctx.put("RESPONSE_CODE", "30");
            ctx.put("ERROR", e.getMessage());
            return PREPARED | NO_JOIN;
        }
    }

    private BigDecimal getOrCreateBalance(String pan) {
        return accountBalances.computeIfAbsent(pan, k -> {
            int balanceAmount = 1000000 + random.nextInt(9000000);
            return new BigDecimal(balanceAmount);
        });
    }

    @Override
    public void commit(long id, Serializable context) {
        Context ctx = (Context) context;
        info("Transaction committed for context: " + id);
    }

    @Override
    public void abort(long id, Serializable context) {
        Context ctx = (Context) context;
        String transactionType = (String) ctx.get("TRANSACTION_TYPE");

        if ("CASH_WITHDRAWAL".equals(transactionType)) {
            String pan = (String) ctx.get("PAN");
            BigDecimal withdrawalAmount = (BigDecimal) ctx.get("WITHDRAWAL_AMOUNT");

            if (pan != null && withdrawalAmount != null) {
                BigDecimal currentBalance = accountBalances.get(pan);
                if (currentBalance != null) {
                    BigDecimal reversedBalance = currentBalance.add(withdrawalAmount);
                    accountBalances.put(pan, reversedBalance);
                    info("Transaction aborted. Balance reversed for PAN: " + pan + ". New balance: " + reversedBalance);
                }
            }
        }

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
            org.jpos.util.LogEvent evt = new org.jpos.util.LogEvent("cash-withdrawal");
            evt.addMessage(msg);
            logger.log(evt);
        }
    }
}
