package com.artivisi.atm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BalanceInquiryRequest {

    @NotBlank(message = "Account number is required")
    private String accountNumber;
}
