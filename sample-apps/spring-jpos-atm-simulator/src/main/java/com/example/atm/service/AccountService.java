package com.example.atm.service;

import com.example.atm.domain.model.Account;
import com.example.atm.domain.repository.AccountRepository;
import com.example.atm.exception.InvalidPinException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public Account findByPan(String pan) {
        return accountRepository.findByPan(pan)
            .orElseThrow(() -> new InvalidPinException("Account not found"));
    }

    public Account findByAccountNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
            .orElseThrow(() -> new InvalidPinException("Account not found"));
    }

    public boolean verifyPin(Account account, String pin) {
        return passwordEncoder.matches(pin, account.getPinHash());
    }

    @Transactional
    public void debit(UUID accountId, BigDecimal amount) {
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new InvalidPinException("Account not found"));

        BigDecimal newBalance = account.getBalance().subtract(amount);
        account.setBalance(newBalance);
        accountRepository.save(account);

        log.info("Account {} debited by {}. New balance: {}",
            account.getPan(), amount, newBalance);
    }

    @Transactional
    public void credit(UUID accountId, BigDecimal amount) {
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new InvalidPinException("Account not found"));

        BigDecimal newBalance = account.getBalance().add(amount);
        account.setBalance(newBalance);
        accountRepository.save(account);

        log.info("Account {} credited by {}. New balance: {}",
            account.getPan(), amount, newBalance);
    }
}
