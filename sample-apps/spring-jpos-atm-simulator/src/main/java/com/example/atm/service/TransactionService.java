package com.example.atm.service;

import com.example.atm.domain.model.Transaction;
import com.example.atm.domain.repository.TransactionRepository;
import com.example.atm.dto.TransactionRequest;
import com.example.atm.dto.TransactionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;

    @Transactional
    public Transaction createTransaction(UUID accountId, TransactionRequest request,
                                        String requestMsg, String responseMsg,
                                        String responseCode, Transaction.TransactionStatus status) {
        Transaction transaction = new Transaction();
        transaction.setIdAccounts(accountId);
        transaction.setType(Transaction.TransactionType.valueOf(request.getType().name()));
        transaction.setAmount(request.getAmount());
        transaction.setStatus(status);
        transaction.setRequestMsg(requestMsg);
        transaction.setResponseMsg(responseMsg);
        transaction.setResponseCode(responseCode);
        transaction.setTerminalId(request.getTerminalId());

        Transaction saved = transactionRepository.save(transaction);
        log.info("Transaction created: {} for account: {}", saved.getId(), accountId);

        return saved;
    }

    public List<Transaction> getAccountTransactions(UUID accountId) {
        return transactionRepository.findByIdAccountsOrderByTimestampDesc(accountId);
    }

    public List<Transaction> getAccountTransactionsBetween(UUID accountId,
                                                           LocalDateTime start,
                                                           LocalDateTime end) {
        return transactionRepository.findByIdAccountsAndTimestampBetween(accountId, start, end);
    }
}
