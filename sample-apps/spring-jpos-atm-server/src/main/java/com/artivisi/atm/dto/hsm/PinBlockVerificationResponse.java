package com.artivisi.atm.dto.hsm;

import com.artivisi.atm.entity.PinEncryptionAlgorithm;
import com.fasterxml.jackson.annotation.JsonIgnore;
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

    /**
     * Internal tracking field - not returned by HSM.
     * Set by client based on request context.
     */
    @JsonIgnore
    private PinEncryptionAlgorithm encryptionAlgorithm;
}
