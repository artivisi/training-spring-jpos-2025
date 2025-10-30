package com.example.atm.jpos.participant;

import lombok.extern.slf4j.Slf4j;
import org.jpos.transaction.Context;
import org.jpos.transaction.TransactionParticipant;

import java.io.Serializable;

@Slf4j
public class BalanceInquiryParticipant implements TransactionParticipant {

    @Override
    public int prepare(long id, Serializable context) {
        Context ctx = (Context) context;
        log.info("Balance Inquiry Participant - prepare");

        return PREPARED | NO_JOIN | READONLY;
    }

    @Override
    public void commit(long id, Serializable context) {
        log.info("Balance Inquiry Participant - commit");
    }

    @Override
    public void abort(long id, Serializable context) {
        log.info("Balance Inquiry Participant - abort");
    }
}
