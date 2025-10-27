package com.muhardin.endy.training.billing.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.muhardin.endy.training.billing.entity.Billing;
import com.muhardin.endy.training.billing.entity.Payment;
import com.muhardin.endy.training.billing.repository.BillingRepository;
import com.muhardin.endy.training.billing.service.BillingServices;

@RestController
@RequestMapping("/api/billings")
public class BillingController {

    @Autowired
    private BillingRepository billingRepository;

    @Autowired 
    private BillingServices billingServices;

    @GetMapping("/")
    public List<Billing> findBillingByProductCodeAndCustomerNumber(String productCode, String customerNumber){
        List<Billing> billings =
         billingRepository.findByProductCodeAndCustomerNumber(productCode, customerNumber);

         System.out.println("Jumlah billing : "+billings.size());

         return billings;
    }

    @PostMapping("/{id}/payments")
    public Payment payBilling(@PathVariable("id") Billing billing){
        return billingServices.pay(billing);
    }
}
