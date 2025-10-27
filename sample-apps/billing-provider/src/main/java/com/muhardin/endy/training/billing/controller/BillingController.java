package com.muhardin.endy.training.billing.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.muhardin.endy.training.billing.entity.Billing;
import com.muhardin.endy.training.billing.entity.Payment;
import com.muhardin.endy.training.billing.repository.BillingRepository;
import com.muhardin.endy.training.billing.repository.PaymentRepository;

@RestController
@RequestMapping("/api/billings")
public class BillingController {

    @Autowired
    private BillingRepository billingRepository;

    @Autowired 
    private PaymentRepository paymentRepository;

    @GetMapping("/")
    public List<Billing> findBillingByProductCodeAndCustomerNumber(String productCode, String customerNumber){
        List<Billing> billings =
         billingRepository.findByProductCodeAndCustomerNumber(productCode, customerNumber);

         System.out.println("Jumlah billing : "+billings.size());

         return billings;
    }

    @PostMapping("/{id}/payments")
    public Payment payBilling(@PathVariable("id") Billing billing){
        Payment p = new Payment();
        p.setBilling(billing);
        p.setAmount(billing.getAmount());
        p.setPaymentReferences(UUID.randomUUID().toString());
        p.setTransactionTime(LocalDateTime.now());

        billing.setPaid(true);
        billingRepository.save(billing);

        paymentRepository.save(p);
        System.out.println("Payment berhasil untuk billing id : "+billing.getId()+", payment reference : "+p.getPaymentReferences()  );
        return p;
    }
}
