package com.ovengers.slotkey.credit.entity;

import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.reservation.entity.Reservation;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "credit_transaction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditTransaction {


    // 크래딧 거래 이력 PK
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 크레딧 거래가 발생한 회원
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    // 거래에서 지급, 차감되는 크레딧 금액
    @Column(nullable = false)
    private int amount;

    // 크레딧 거래 유형
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CreditTransactionType type;

    // 크레딧 거래와 관련된 예약
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    // 해당 거래 처리 후 회원의 크레딧 잔액
    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    // 크레딧 거래 사유
    @Column(length = 500)
    private String reason;

    // 크레딧 거래가 발생한 시각
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // 크레딧 거래 이력 생성
    public CreditTransaction(
            Member member,
            int amount,
            CreditTransactionType type,
            Reservation reservation,
            int balanceAfter,
            String reason,
            LocalDateTime createdAt
    ) {
        this.member = member;
        this.amount = amount;
        this.type = type;
        this.reservation = reservation;
        this.balanceAfter = balanceAfter;
        this.reason = reason;
        this.createdAt = createdAt;
    }
}