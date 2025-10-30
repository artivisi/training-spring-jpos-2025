package com.example.atm.jpos.participant;

import com.example.atm.dto.WithdrawalRequest;
import com.example.atm.dto.WithdrawalResponse;
import com.example.atm.exception.AccountNotActiveException;
import com.example.atm.exception.AccountNotFoundException;
import com.example.atm.exception.InsufficientBalanceException;
import com.example.atm.jpos.SpringBeanFactory;
import com.example.atm.service.BankService;
import lombok.extern.slf4j.Slf4j;
import org.jpos.iso.ISOMsg;
import org.jpos.transaction.Context;
import org.jpos.transaction.TransactionParticipant;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * jPOS TransactionParticipant for withdrawal operations.
 * Note: This class is NOT managed by Spring - it's instantiated by jPOS Q2.
 * Spring beans are accessed via SpringBeanFactory.
 */
@Slf4j
public class WithdrawalParticipant implements TransactionParticipant {

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
                return PREPARED | NO_JOIN | READONLY;
            }

            // Skip processing if an error response code is already set by previous participants
            String existingResponseCode = (String) ctx.get("RESPONSE_CODE");
            if (existingResponseCode != null && !"00".equals(existingResponseCode)) {
                log.debug("Skipping withdrawal - error response code already set: {}", existingResponseCode);
                return PREPARED | NO_JOIN | READONLY;
            }

            String processingCode = null;
            try {
                processingCode = msg.getString(3);
            } catch (Exception e) {
                log.debug("Error getting processing code: {}", e.getMessage());
                return PREPARED | NO_JOIN | READONLY;
            }

            if (!"010000".equals(processingCode)) {
                log.debug("Skipping withdrawal participant, processing code: {}", processingCode);
                return PREPARED | NO_JOIN | READONLY;
            }

            String accountNumber = msg.getString(102);
            String amountStr = msg.getString(4);

            if (accountNumber == null || accountNumber.isEmpty()) {
                log.error("Account number not found in field 102");
                ctx.put("RESPONSE_CODE", "30");
                return PREPARED | NO_JOIN;
            }

            if (amountStr == null || amountStr.isEmpty()) {
                log.error("Amount not found in field 4");
                ctx.put("RESPONSE_CODE", "30");
                return PREPARED | NO_JOIN;
            }

            BigDecimal amount = new BigDecimal(amountStr).movePointLeft(2);

            log.info("Processing withdrawal for account: {} amount: {}", accountNumber, amount);

            WithdrawalRequest request = WithdrawalRequest.builder()
                    .accountNumber(accountNumber)
                    .amount(amount)
                    .build();

            WithdrawalResponse response = getBankService().withdraw(request);

            ctx.put("WITHDRAWAL_AMOUNT", response.getWithdrawalAmount());
            ctx.put("BALANCE_BEFORE", response.getBalanceBefore());
            ctx.put("BALANCE_AFTER", response.getBalanceAfter());
            ctx.put("RESPONSE_CODE", "00");
            ctx.put("REFERENCE_NUMBER", response.getReferenceNumber());

            return PREPARED;

        } catch (AccountNotFoundException e) {
            log.error("Account not found: {}", e.getMessage());
            ctx.put("RESPONSE_CODE", "14");
            return PREPARED | NO_JOIN;
        } catch (InsufficientBalanceException e) {
            log.error("Insufficient balance: {}", e.getMessage());
            ctx.put("RESPONSE_CODE", "51");
            return PREPARED | NO_JOIN;
        } catch (AccountNotActiveException e) {
            log.error("Account not active: {}", e.getMessage());
            ctx.put("RESPONSE_CODE", "62");
            return PREPARED | NO_JOIN;
        } catch (Exception e) {
            log.error("Unexpected error in WithdrawalParticipant: ", e);
            ctx.put("RESPONSE_CODE", "96");
            return PREPARED | NO_JOIN;
        }
    }

    @Override
    public void commit(long id, Serializable context) {
        log.info("Transaction {} committed", id);
    }

    @Override
    public void abort(long id, Serializable context) {
        log.info("Transaction {} aborted", id);
    }
}
