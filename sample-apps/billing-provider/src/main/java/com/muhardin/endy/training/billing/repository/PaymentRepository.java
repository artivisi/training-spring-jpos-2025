package com.muhardin.endy.training.billing.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.muhardin.endy.training.billing.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    
}
