package com.artivisi.atm.repository;

import com.artivisi.atm.entity.CryptoKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CryptoKeyRepository extends JpaRepository<CryptoKey, UUID> {

    /**
     * Find active key for a terminal and key type.
     * Returns the most recent active key based on effective_from.
     */
    @Query("SELECT k FROM CryptoKey k WHERE k.terminalId = :terminalId " +
           "AND k.keyType = :keyType " +
           "AND k.status = 'ACTIVE' " +
           "ORDER BY k.effectiveFrom DESC")
    Optional<CryptoKey> findActiveKey(@Param("terminalId") String terminalId,
                                       @Param("keyType") CryptoKey.KeyType keyType);

    /**
     * Find key by terminal, type, and version.
     * Used for fallback during key rotation.
     */
    Optional<CryptoKey> findByTerminalIdAndKeyTypeAndKeyVersion(String terminalId,
                                                                  CryptoKey.KeyType keyType,
                                                                  Integer keyVersion);

    /**
     * Find all keys for a terminal and type, ordered by version descending.
     * Useful during transition periods when multiple keys may be valid.
     */
    @Query("SELECT k FROM CryptoKey k WHERE k.terminalId = :terminalId " +
           "AND k.keyType = :keyType " +
           "AND k.status IN ('ACTIVE', 'PENDING') " +
           "ORDER BY k.keyVersion DESC")
    List<CryptoKey> findValidKeysForTerminal(@Param("terminalId") String terminalId,
                                              @Param("keyType") CryptoKey.KeyType keyType);

    /**
     * Find the highest version number for a terminal and key type.
     */
    @Query("SELECT COALESCE(MAX(k.keyVersion), 0) FROM CryptoKey k " +
           "WHERE k.terminalId = :terminalId AND k.keyType = :keyType")
    Integer findMaxVersion(@Param("terminalId") String terminalId,
                          @Param("keyType") CryptoKey.KeyType keyType);

    /**
     * Find all active and pending keys for a terminal (all key types).
     */
    @Query("SELECT k FROM CryptoKey k WHERE k.terminalId = :terminalId " +
           "AND k.status IN ('ACTIVE', 'PENDING') " +
           "ORDER BY k.keyType, k.keyVersion DESC")
    List<CryptoKey> findAllValidKeysForTerminal(@Param("terminalId") String terminalId);
}
