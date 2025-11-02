package com.artivisi.atm.jpos.participant;

import lombok.extern.slf4j.Slf4j;
import org.jpos.transaction.Context;
import org.jpos.transaction.TransactionParticipant;

import java.io.Serializable;

@Slf4j
public class PinVerificationParticipant implements TransactionParticipant {

    @Override
    public int prepare(long id, Serializable context) {
        Context ctx = (Context) context;
        log.info("PIN Verification Participant - prepare");

        return PREPARED | NO_JOIN | READONLY;
    }

    @Override
    public void commit(long id, Serializable context) {
        log.info("PIN Verification Participant - commit");
    }

    @Override
    public void abort(long id, Serializable context) {
        log.info("PIN Verification Participant - abort");
    }
}
