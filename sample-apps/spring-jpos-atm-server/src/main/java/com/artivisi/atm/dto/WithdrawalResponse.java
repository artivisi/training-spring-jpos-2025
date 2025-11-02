package com.artivisi.atm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WithdrawalResponse {

    private String accountNumber;
    private String accountHolderName;
    private BigDecimal withdrawalAmount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private String currency;
    private LocalDateTime timestamp;
    private String referenceNumber;
}
