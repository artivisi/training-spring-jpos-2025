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
public class PinBlockVerificationResponse {
    private boolean valid;
    private String message;
    private String terminalId;
    private String pan;
    private String pinFormat;
    private String lmkKeyId;
    private String tpkKeyId;
    private PinEncryptionAlgorithm encryptionAlgorithm;
}
