package com.example.atm.controller;

import com.example.atm.dto.BalanceInquiryRequest;
import com.example.atm.dto.BalanceInquiryResponse;
import com.example.atm.dto.WithdrawalRequest;
import com.example.atm.dto.WithdrawalResponse;
import com.example.atm.service.BankService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bank")
@RequiredArgsConstructor
@Slf4j
public class BankController {

    private final BankService bankService;

    @PostMapping("/balance-inquiry")
    public ResponseEntity<BalanceInquiryResponse> balanceInquiry(
            @Valid @RequestBody BalanceInquiryRequest request) {
        log.info("Received balance inquiry request for account: {}", request.getAccountNumber());
        BalanceInquiryResponse response = bankService.balanceInquiry(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/withdrawal")
    public ResponseEntity<WithdrawalResponse> withdraw(
            @Valid @RequestBody WithdrawalRequest request) {
        log.info("Received withdrawal request for account: {} amount: {}",
                request.getAccountNumber(), request.getAmount());
        WithdrawalResponse response = bankService.withdraw(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
