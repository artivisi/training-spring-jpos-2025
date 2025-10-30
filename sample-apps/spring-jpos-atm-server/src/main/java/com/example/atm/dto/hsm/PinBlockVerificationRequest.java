package com.example.atm.dto.hsm;

import com.example.atm.entity.PinEncryptionAlgorithm;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PinBlockVerificationRequest {
    private String pinBlockUnderTPK;
    private String pinBlockUnderLMK;
    private String terminalId;
    private String pan;
    private PinFormat pinFormat;
    private PinEncryptionAlgorithm encryptionAlgorithm;
}
