package com.ovengers.slotkey.credit.repository;


import com.ovengers.slotkey.credit.entity.CreditTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, Long> {
}