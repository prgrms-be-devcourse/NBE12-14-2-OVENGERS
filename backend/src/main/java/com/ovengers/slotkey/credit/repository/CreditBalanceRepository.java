package com.ovengers.slotkey.credit.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CreditBalanceRepository {

    private final EntityManager entityManager;

    // 잔액이 충분한 경우에만 크레딧 차감
    public int decreaseIfEnough(
            Long memberId,
            int amount
    ) {
        return entityManager.createNativeQuery("""
                UPDATE member
                SET balance = balance - :amount
                WHERE id = :memberId
                  AND balance >= :amount
                """)
                .setParameter("amount", amount)
                .setParameter("memberId", memberId)
                .executeUpdate();
    }

    // 크레딧 증가
    public int increase(
            Long memberId,
            int amount
    ) {
        return entityManager.createNativeQuery("""
                UPDATE member
                SET balance = balance + :amount
                WHERE id = :memberId
                """)
                .setParameter("amount", amount)
                .setParameter("memberId", memberId)
                .executeUpdate();
    }

    // 현재 크레딧 잔액 조회
    public int findBalance(Long memberId) {
        Number balance = (Number) entityManager.createNativeQuery("""
                SELECT balance
                FROM member
                WHERE id = :memberId
                """)
                .setParameter("memberId", memberId)
                .getSingleResult();

        return balance.intValue();
    }
}