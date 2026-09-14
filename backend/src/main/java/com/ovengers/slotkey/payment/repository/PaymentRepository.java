package com.ovengers.slotkey.payment.repository;

import com.ovengers.slotkey.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
}
