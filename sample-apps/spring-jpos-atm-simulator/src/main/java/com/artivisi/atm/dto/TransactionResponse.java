package com.artivisi.atm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {

    private String responseCode;
    private String responseMessage;
    private BigDecimal balance;
    private BigDecimal amount;
    private LocalDateTime timestamp;
    private String terminalId;
    private String transactionId;
}
