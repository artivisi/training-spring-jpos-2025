package com.example.atm.service;

import com.example.atm.config.HsmProperties;
import com.example.atm.dto.hsm.PinBlockVerificationRequest;
import com.example.atm.dto.hsm.PinBlockVerificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class HsmService {

    private final HsmClient hsmClient;
    private final HsmProperties hsmProperties;

    public boolean verifyPinBlock(String pinBlockUnderTPK, String pinBlockUnderLMK, String pan) {
        try {
            log.info("Verifying PIN block with HSM using Method A (Verify with Translation)");

            PinBlockVerificationRequest request = PinBlockVerificationRequest.builder()
                    .pinBlockUnderTPK(pinBlockUnderTPK)
                    .pinBlockUnderLMK(pinBlockUnderLMK)
                    .terminalId(hsmProperties.getPin().getTerminalId())
                    .pan(pan)
                    .pinFormat(hsmProperties.getPin().getFormat())
                    .build();

            PinBlockVerificationResponse response = hsmClient.verifyPinBlock(request);

            if (response.isValid()) {
                log.info("PIN verification successful. TPK: {}, LMK: {}, Format: {}",
                        response.getTpkKeyId(), response.getLmkKeyId(), response.getPinFormat());
                return true;
            } else {
                log.warn("PIN verification failed. Message: {}", response.getMessage());
                return false;
            }

        } catch (Exception e) {
            log.error("Error communicating with HSM. Error: {}", e.getMessage(), e);
            throw new RuntimeException("HSM communication error", e);
        }
    }
}
