package com.artivisi.atm.jpos.participant;

import com.artivisi.atm.dto.BalanceInquiryRequest;
import com.artivisi.atm.dto.BalanceInquiryResponse;
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
 * jPOS TransactionParticipant for balance inquiry operations.
 * Note: This class is NOT managed by Spring - it's instantiated by jPOS Q2.
 * Spring beans are accessed via SpringBeanFactory.
 */
@Slf4j
public class BalanceInquiryParticipant implements TransactionParticipant {

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

            String mti = msg.getMTI();
            // Only process financial transactions (0200), skip network management (0800)
            if (!"0200".equals(mti)) {
                log.debug("Skipping balance inquiry for MTI: {}", mti);
                return PREPARED | NO_JOIN | READONLY;
            }

            // Skip processing if an error response code is already set by previous participants
            String existingResponseCode = (String) ctx.get("RESPONSE_CODE");
            if (existingResponseCode != null && !"00".equals(existingResponseCode)) {
                log.debug("Skipping balance inquiry - error response code already set: {}", existingResponseCode);
                return PREPARED | NO_JOIN | READONLY;
            }

            String processingCode = msg.getString(3);
            if (!"310000".equals(processingCode)) {
                return PREPARED | NO_JOIN | READONLY;
            }

            String accountNumber = msg.getString(102);
            if (accountNumber == null || accountNumber.isEmpty()) {
                log.error("Account number not found in field 102");
                ctx.put("RESPONSE_CODE", "30");
                return PREPARED | NO_JOIN;
            }

            log.info("Processing balance inquiry for account: {}", accountNumber);

            BalanceInquiryRequest request = BalanceInquiryRequest.builder()
                    .accountNumber(accountNumber)
                    .build();

            BalanceInquiryResponse response = getBankService().balanceInquiry(request);

            ctx.put("BALANCE", response.getBalance());
            ctx.put("RESPONSE_CODE", "00");
            ctx.put("ACCOUNT_HOLDER_NAME", response.getAccountHolderName());

            return PREPARED | NO_JOIN;

        } catch (AccountNotFoundException e) {
            log.error("Account not found: {}", e.getMessage());
            ctx.put("RESPONSE_CODE", "14");
            return PREPARED | NO_JOIN;
        } catch (AccountNotActiveException e) {
            log.error("Account not active: {}", e.getMessage());
            ctx.put("RESPONSE_CODE", "62");
            return PREPARED | NO_JOIN;
        } catch (Exception e) {
            log.error("Error processing balance inquiry: ", e);
            ctx.put("RESPONSE_CODE", "96");
            return PREPARED | NO_JOIN;
        }
    }

    @Override
    public void commit(long id, Serializable context) {
    }

    @Override
    public void abort(long id, Serializable context) {
    }
}
