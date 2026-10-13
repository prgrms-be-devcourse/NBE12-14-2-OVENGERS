package com.ovengers.slotkey.credit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "charge_order",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_charge_order_order_id",
                columnNames = "order_id"
        )
)
public class ChargeOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, length = 64, unique = true)
    private String orderId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChargeOrderStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ChargeOrder() {
    }

    private ChargeOrder(
            Long memberId,
            String orderId,
            long amount,
            ChargeOrderStatus status,
            LocalDateTime createdAt
    ) {
        this.memberId = memberId;
        this.orderId = orderId;
        this.amount = amount;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static ChargeOrder createPending(
            Long memberId,
            String orderId,
            long amount,
            LocalDateTime createdAt
    ) {
        return new ChargeOrder(
                memberId,
                orderId,
                amount,
                ChargeOrderStatus.PENDING,
                createdAt
        );
    }

    public Long getId() {
        return id;
    }

    public String getOrderId() {
        return orderId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public long getAmount() {
        return amount;
    }

    public ChargeOrderStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
