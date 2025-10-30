package com.example.atm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransactionRequest {

    @NotBlank(message = "PAN is required")
    @Pattern(regexp = "\\d{13,19}", message = "PAN must be 13-19 digits")
    private String pan;

    @NotBlank(message = "PIN is required")
    @Pattern(regexp = "\\d{4,6}", message = "PIN must be 4-6 digits")
    private String pin;

    @NotNull(message = "Transaction type is required")
    private TransactionType type;

    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    private String terminalId = "ATM00001";

    public enum TransactionType {
        BALANCE,
        WITHDRAWAL
    }
}
