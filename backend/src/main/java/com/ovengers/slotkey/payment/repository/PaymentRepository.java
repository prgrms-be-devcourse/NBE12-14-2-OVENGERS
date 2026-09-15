package com.ovengers.slotkey.payment.repository;

import com.ovengers.slotkey.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByReservation_Id(Long reservationId);
}
