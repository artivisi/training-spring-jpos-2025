package com.example.atm.api.controller;

import com.example.atm.dto.TransactionRequest;
import com.example.atm.dto.TransactionResponse;
import com.example.atm.service.AtmTransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API controller for ATM transactions.
 * Provides JSON endpoints for balance inquiry and withdrawal operations.
 * Used by testing tools and external integrations.
 */
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionApiController {

    private final AtmTransactionService atmTransactionService;

    /**
     * Execute balance inquiry transaction.
     *
     * @param request Balance inquiry request
     * @return Transaction response with balance
     */
    @PostMapping("/balance")
    public ResponseEntity<TransactionResponse> balanceInquiry(@Valid @RequestBody TransactionRequest request) {
        log.info("API: Balance inquiry request for PAN: {}", maskPan(request.getPan()));

        // Ensure transaction type is set to BALANCE
        request.setType(TransactionRequest.TransactionType.BALANCE);

        TransactionResponse response = atmTransactionService.executeTransaction(request);

        return ResponseEntity.ok(response);
    }

    /**
     * Execute withdrawal transaction.
     *
     * @param request Withdrawal request with amount
     * @return Transaction response with balance
     */
    @PostMapping("/withdrawal")
    public ResponseEntity<TransactionResponse> withdrawal(@Valid @RequestBody TransactionRequest request) {
        log.info("API: Withdrawal request for PAN: {}, amount: {}",
                maskPan(request.getPan()), request.getAmount());

        // Ensure transaction type is set to WITHDRAWAL
        request.setType(TransactionRequest.TransactionType.WITHDRAWAL);

        TransactionResponse response = atmTransactionService.executeTransaction(request);

        return ResponseEntity.ok(response);
    }

    /**
     * Mask PAN for logging (show first 6 and last 4 digits).
     */
    private String maskPan(String pan) {
        if (pan == null || pan.length() < 10) {
            return "****";
        }
        return pan.substring(0, 6) + "****" + pan.substring(pan.length() - 4);
    }
}
