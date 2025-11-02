package com.artivisi.atm.domain.repository;

import com.artivisi.atm.domain.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    List<Transaction> findByIdAccountsOrderByTimestampDesc(UUID idAccounts);

    List<Transaction> findByIdAccountsAndTimestampBetween(UUID idAccounts, LocalDateTime start, LocalDateTime end);
}
