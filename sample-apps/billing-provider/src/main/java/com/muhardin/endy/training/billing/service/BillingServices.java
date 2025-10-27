package com.muhardin.endy.training.billing.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.muhardin.endy.training.billing.entity.Billing;
import com.muhardin.endy.training.billing.entity.Payment;
import com.muhardin.endy.training.billing.repository.BillingRepository;
import com.muhardin.endy.training.billing.repository.PaymentRepository;

@Service
public class BillingServices {

    @Autowired private BillingRepository billingRepository;
    @Autowired private PaymentRepository paymentRepository;

    @Transactional
    public Payment pay(Billing billing) {
        billing.setPaid(true);
        billingRepository.save(billing);

        Payment p = new Payment();
        p.setBilling(billing);
        p.setAmount(billing.getAmount());
        p.setPaymentReferences(UUID.randomUUID().toString());
        p.setTransactionTime(LocalDateTime.now());

        paymentRepository.save(p);
        return p;
    }
}
