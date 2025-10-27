package com.muhardin.endy.training.billing.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.muhardin.endy.training.billing.entity.Billing;
import com.muhardin.endy.training.billing.repository.BillingRepository;

@RestController
@RequestMapping("/api/billings")
public class BillingController {

    @Autowired
    private BillingRepository billingRepository;

    @GetMapping("/")
    public List<Billing> findBillingByProductCodeAndCustomerNumber(String productCode, String customerNumber){
        List<Billing> billings =
         billingRepository.findByProductCodeAndCustomerNumber(productCode, customerNumber);

         System.out.println("Jumlah billing : "+billings.size());

         return billings;
    }
}
