package com.ovengers.slotkey.credit.entity;

import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.reservation.entity.Reservation;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "credit_transaction",
        indexes = {
                @Index(
                        name = "idx_credit_transaction_member_created_at",
                        columnList = "member_id, created_at"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class CreditTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CreditTransactionType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    @Column(name = "balance_after", nullable = false)
    private Integer balanceAfter;

    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public CreditTransaction(
            Member member,
            Integer amount,
            CreditTransactionType type,
            Reservation reservation,
            Integer balanceAfter,
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