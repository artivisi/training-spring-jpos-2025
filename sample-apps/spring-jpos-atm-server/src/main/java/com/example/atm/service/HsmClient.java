package com.example.atm.service;

import com.example.atm.dto.hsm.PinBlockVerificationRequest;
import com.example.atm.dto.hsm.PinBlockVerificationResponse;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface HsmClient {

    @PostExchange
    PinBlockVerificationResponse verifyPinBlock(PinBlockVerificationRequest request);
}
