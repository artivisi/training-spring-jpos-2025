package com.example.atm.service;

import com.example.atm.dto.BalanceInquiryRequest;
import com.example.atm.dto.BalanceInquiryResponse;
import com.example.atm.dto.WithdrawalRequest;
import com.example.atm.dto.WithdrawalResponse;
import com.example.atm.entity.Account;
import com.example.atm.entity.Transaction;
import com.example.atm.exception.AccountNotActiveException;
import com.example.atm.exception.AccountNotFoundException;
import com.example.atm.exception.InsufficientBalanceException;
import com.example.atm.repository.AccountRepository;
import com.example.atm.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BankService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final HsmService hsmService;

    @Transactional(readOnly = true)
    public BalanceInquiryResponse balanceInquiry(BalanceInquiryRequest request) {
        log.info("Processing balance inquiry for account: {}", request.getAccountNumber());

        Account account = accountRepository.findByAccountNumber(request.getAccountNumber())
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found: " + request.getAccountNumber()));

        if (account.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(
                    "Account is not active: " + request.getAccountNumber());
        }

        String referenceNumber = generateReferenceNumber();
        LocalDateTime timestamp = LocalDateTime.now();

        log.info("Balance inquiry completed for account: {} with reference: {}",
                request.getAccountNumber(), referenceNumber);

        return BalanceInquiryResponse.builder()
                .accountNumber(account.getAccountNumber())
                .accountHolderName(account.getAccountHolderName())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .accountType(account.getAccountType().name())
                .timestamp(timestamp)
                .referenceNumber(referenceNumber)
                .build();
    }

    @Transactional
    public WithdrawalResponse withdraw(WithdrawalRequest request) {
        log.info("Processing withdrawal for account: {} amount: {}",
                request.getAccountNumber(), request.getAmount());

        Account account = accountRepository.findByAccountNumberWithLock(request.getAccountNumber())
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found: " + request.getAccountNumber()));

        if (account.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(
                    "Account is not active: " + request.getAccountNumber());
        }

        if (account.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient balance. Current balance: " + account.getBalance() +
                    ", requested: " + request.getAmount());
        }

        BigDecimal balanceBefore = account.getBalance();
        BigDecimal balanceAfter = balanceBefore.subtract(request.getAmount());

        account.setBalance(balanceAfter);
        accountRepository.save(account);

        String referenceNumber = generateReferenceNumber();
        LocalDateTime timestamp = LocalDateTime.now();

        Transaction transaction = Transaction.builder()
                .account(account)
                .transactionType(Transaction.TransactionType.WITHDRAWAL)
                .amount(request.getAmount())
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .description("Cash withdrawal")
                .referenceNumber(referenceNumber)
                .transactionDate(timestamp)
                .build();

        transactionRepository.save(transaction);

        log.info("Withdrawal completed for account: {} with reference: {}",
                request.getAccountNumber(), referenceNumber);

        return WithdrawalResponse.builder()
                .accountNumber(account.getAccountNumber())
                .accountHolderName(account.getAccountHolderName())
                .withdrawalAmount(request.getAmount())
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .currency(account.getCurrency())
                .timestamp(timestamp)
                .referenceNumber(referenceNumber)
                .build();
    }

    /**
     * Verify PIN against account's stored credentials.
     * Uses account's configured verification method (ENCRYPTED_PIN_BLOCK or PVV).
     *
     * @param accountNumber Account number to verify PIN for
     * @param pinBlock PIN block from terminal, encrypted under TPK
     * @param pan Primary Account Number
     */
    public void verifyPin(String accountNumber, String pinBlock, String pan) {
        log.info("Verifying PIN for account: {}", accountNumber);

        if (pinBlock == null || pinBlock.isEmpty()) {
            log.debug("No PIN block provided for account: {}, skipping verification", accountNumber);
            return;
        }

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found: " + accountNumber));

        boolean pinValid = hsmService.verifyPin(pinBlock, pan, account);

        if (!pinValid) {
            log.warn("Invalid PIN for account: {}", accountNumber);
            throw new RuntimeException("Invalid PIN");
        }

        log.info("PIN verified successfully for account: {}", accountNumber);
    }

    private String generateReferenceNumber() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }
}
