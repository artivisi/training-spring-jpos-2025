package com.example.atm.dto;

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
public class BalanceInquiryResponse {

    private String accountNumber;
    private String accountHolderName;
    private BigDecimal balance;
    private String currency;
    private String accountType;
    private LocalDateTime timestamp;
    private String referenceNumber;
}
