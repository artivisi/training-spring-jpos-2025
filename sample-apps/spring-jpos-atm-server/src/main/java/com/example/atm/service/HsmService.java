package com.example.atm.service;

import com.example.atm.entity.Account;
import com.example.atm.entity.PinVerificationType;
import com.example.atm.service.strategy.PinVerificationStrategy;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service for HSM operations including PIN verification.
 * Uses Strategy pattern to support multiple verification methods.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HsmService {

    private final List<PinVerificationStrategy> strategies;
    private Map<PinVerificationType, PinVerificationStrategy> strategyMap;

    @PostConstruct
    public void init() {
        strategyMap = strategies.stream()
                .collect(Collectors.toMap(
                        PinVerificationStrategy::getType,
                        Function.identity()
                ));
        log.info("Initialized {} PIN verification strategies: {}",
                strategyMap.size(), strategyMap.keySet());
    }

    /**
     * Verify PIN using the account's configured verification method.
     *
     * @param pinBlockFromTerminal PIN block from terminal, encrypted under TPK
     * @param pan Primary Account Number
     * @param account Account entity containing verification type and stored credentials
     * @return true if PIN is valid, false otherwise
     */
    public boolean verifyPin(String pinBlockFromTerminal, String pan, Account account) {
        PinVerificationType type = account.getPinVerificationType();
        PinVerificationStrategy strategy = strategyMap.get(type);

        if (strategy == null) {
            log.error("No strategy found for verification type: {}", type);
            throw new RuntimeException("Unsupported PIN verification type: " + type);
        }

        log.info("Using {} verification strategy for account: {}",
                type, account.getAccountNumber());

        return strategy.verify(pinBlockFromTerminal, pan, account);
    }
}
