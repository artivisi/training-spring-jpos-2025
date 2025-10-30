package com.example.atm.dto.hsm;

import com.example.atm.entity.PinEncryptionAlgorithm;
import com.fasterxml.jackson.annotation.JsonIgnore;
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

    /**
     * Internal tracking field - not sent to HSM.
     * HSM determines algorithm from PIN block format and length.
     */
    @JsonIgnore
    private PinEncryptionAlgorithm encryptionAlgorithm;
}
