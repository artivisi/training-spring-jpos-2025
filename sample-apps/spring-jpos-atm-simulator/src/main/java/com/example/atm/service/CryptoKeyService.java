package com.example.atm.service;

import com.example.atm.domain.model.CryptoKey;
import com.example.atm.domain.repository.CryptoKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class CryptoKeyService {

    private final CryptoKeyRepository cryptoKeyRepository;

    public CryptoKey getActiveKey(CryptoKey.KeyType keyType) {
        return cryptoKeyRepository.findByKeyTypeAndStatus(keyType, CryptoKey.KeyStatus.ACTIVE)
            .orElseThrow(() -> new RuntimeException("No active key found for type: " + keyType));
    }

    @Transactional
    public CryptoKey rotateKey(CryptoKey.KeyType keyType, String newKeyValue, String checkValue) {
        cryptoKeyRepository.findByKeyTypeAndStatus(keyType, CryptoKey.KeyStatus.ACTIVE)
            .ifPresent(existingKey -> {
                existingKey.setStatus(CryptoKey.KeyStatus.EXPIRED);
                cryptoKeyRepository.save(existingKey);
                log.info("Expired old {} key: {}", keyType, existingKey.getId());
            });

        CryptoKey newKey = new CryptoKey();
        newKey.setKeyType(keyType);
        newKey.setKeyValue(newKeyValue);
        newKey.setCheckValue(checkValue);
        newKey.setStatus(CryptoKey.KeyStatus.ACTIVE);
        newKey.setExpiresAt(LocalDateTime.now().plusDays(90));

        CryptoKey saved = cryptoKeyRepository.save(newKey);
        log.info("Created new {} key: {} with check value: {}", keyType, saved.getId(), checkValue);

        return saved;
    }
}
