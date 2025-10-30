package com.example.atm.domain.repository;

import com.example.atm.domain.model.CryptoKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CryptoKeyRepository extends JpaRepository<CryptoKey, UUID> {

    Optional<CryptoKey> findByKeyTypeAndStatus(CryptoKey.KeyType keyType, CryptoKey.KeyStatus status);
}
