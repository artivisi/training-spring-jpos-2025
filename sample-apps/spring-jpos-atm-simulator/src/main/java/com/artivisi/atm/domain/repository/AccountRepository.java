package com.artivisi.atm.domain.repository;

import com.artivisi.atm.domain.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByPan(String pan);

    Optional<Account> findByAccountNumber(String accountNumber);

    boolean existsByPan(String pan);

    boolean existsByAccountNumber(String accountNumber);
}
