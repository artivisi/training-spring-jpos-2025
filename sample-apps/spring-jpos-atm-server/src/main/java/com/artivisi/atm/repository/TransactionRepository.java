package com.artivisi.atm.repository;

import com.artivisi.atm.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByReferenceNumber(String referenceNumber);

    List<Transaction> findByAccountIdOrderByTransactionDateDesc(Long accountId);

    Page<Transaction> findByAccountIdOrderByTransactionDateDesc(Long accountId, Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE t.account.id = :accountId " +
           "AND t.transactionDate BETWEEN :startDate AND :endDate " +
           "ORDER BY t.transactionDate DESC")
    List<Transaction> findByAccountIdAndDateRange(Long accountId,
                                                   LocalDateTime startDate,
                                                   LocalDateTime endDate);

    List<Transaction> findByAccountIdAndTransactionType(Long accountId,
                                                         Transaction.TransactionType transactionType);
}
