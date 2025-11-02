package com.artivisi.atm.jpos.participant;

import com.artivisi.atm.exception.AccountNotActiveException;
import com.artivisi.atm.exception.AccountNotFoundException;
import com.artivisi.atm.jpos.SpringBeanFactory;
import com.artivisi.atm.service.BankService;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOMsg;
import org.jpos.transaction.Context;
import org.jpos.transaction.TransactionParticipant;

import java.io.Serializable;

/**
 * jPOS TransactionParticipant for account validation.
 * Validates that the account exists and is active before proceeding to PIN verification.
 * This prevents unnecessary PIN verification for non-existent accounts.
 * Note: This class is NOT managed by Spring - it's instantiated by jPOS Q2.
 */
@Slf4j
public class AccountValidationParticipant implements TransactionParticipant {

    private BankService getBankService() {
        return SpringBeanFactory.getBean(BankService.class);
    }

    @Override
    public int prepare(long id, Serializable context) {
        Context ctx = (Context) context;
        try {
            ISOMsg msg = (ISOMsg) ctx.get("REQUEST");

            if (msg == null) {
                log.error("No ISO message in context");
                ctx.put("RESPONSE_CODE", "96");
                return PREPARED | NO_JOIN | READONLY;
            }

            String mti = msg.getMTI();
            // Only process financial transactions (0200), skip network management (0800)
            if (!"0200".equals(mti)) {
                log.debug("Skipping account validation for MTI: {}", mti);
                return PREPARED | NO_JOIN | READONLY;
            }

            String accountNumber = msg.getString(102);

            if (accountNumber == null || accountNumber.isEmpty()) {
                log.error("Account number not found in field 102");
                ctx.put("RESPONSE_CODE", "30");
                return PREPARED | NO_JOIN | READONLY;
            }

            log.debug("Validating account: {}", accountNumber);

            // Just check if account exists and is active - don't fetch balance yet
            getBankService().validateAccount(accountNumber);

            log.debug("Account validation successful for: {}", accountNumber);
            return PREPARED | NO_JOIN | READONLY;

        } catch (AccountNotFoundException e) {
            log.error("Account not found: {}", e.getMessage());
            ctx.put("RESPONSE_CODE", "14");
            return PREPARED | NO_JOIN | READONLY;
        } catch (AccountNotActiveException e) {
            log.error("Account not active: {}", e.getMessage());
            ctx.put("RESPONSE_CODE", "62");
            return PREPARED | NO_JOIN | READONLY;
        } catch (Exception e) {
            log.error("Error validating account: {}", e.getMessage());
            ctx.put("RESPONSE_CODE", "96");
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
