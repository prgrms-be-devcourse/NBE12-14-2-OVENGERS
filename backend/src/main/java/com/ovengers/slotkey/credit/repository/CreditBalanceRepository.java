package com.ovengers.slotkey.credit.repository;

import com.ovengers.slotkey.member.entity.Member;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface CreditBalanceRepository extends Repository<Member, Long> {

    // 잔액이 충분한 경우에만 차감
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Member m
            SET m.balance = m.balance - :amount
            WHERE m.id = :memberId
              AND m.balance >= :amount
            """)
    int decreaseIfEnough(
            @Param("memberId") Long memberId,
            @Param("amount") int amount
    );

    // 잔액 증가
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Member m
            SET m.balance = m.balance + :amount
            WHERE m.id = :memberId
            """)
    int increase(
            @Param("memberId") Long memberId,
            @Param("amount") int amount
    );
}