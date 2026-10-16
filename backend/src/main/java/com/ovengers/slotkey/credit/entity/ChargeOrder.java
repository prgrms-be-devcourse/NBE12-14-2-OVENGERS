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
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_charge_order_order_id",
                        columnNames = "order_id"
                ),
                @UniqueConstraint(
                        name = "uk_charge_order_member_client_idempotency",
                        columnNames = {"member_id", "client_idempotency_key"}
                )
        }
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

    @Column(name = "payment_key", length = 200, unique = true)
    private String paymentKey;

    @Column(name = "client_idempotency_key", length = 100)
    private String clientIdempotencyKey;

    @Column(name = "pg_idempotency_key", length = 100, unique = true)
    private String pgIdempotencyKey;

    @Column(name = "payment_method", length = 30)
    private String paymentMethod;

    @Column(name = "payment_provider", length = 30)
    private String paymentProvider;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "recovery_next_attempt_at")
    private LocalDateTime recoveryNextAttemptAt;

    @Column(name = "recovery_exhausted_at")
    private LocalDateTime recoveryExhaustedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

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
        this.updatedAt = createdAt;
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

    public String getPaymentKey() {
        return paymentKey;
    }

    public String getPgIdempotencyKey() {
        return pgIdempotencyKey;
    }

    public String getClientIdempotencyKey() {
        return clientIdempotencyKey;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getRecoveryNextAttemptAt() {
        return recoveryNextAttemptAt;
    }

    public LocalDateTime getRecoveryExhaustedAt() {
        return recoveryExhaustedAt;
    }

    /** PG가 알려 준 결제수단(표시용). 결제 처리와 무관하며 값이 없으면 기록하지 않는다. */
    public void recordPaymentMethod(String paymentMethod, String paymentProvider) {
        this.paymentMethod = paymentMethod;
        this.paymentProvider = paymentProvider;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public String getPaymentProvider() {
        return paymentProvider;
    }

    public void markPaid(LocalDateTime now) {
        this.status = ChargeOrderStatus.PAID;
        this.failureCode = null;
        this.recoveryNextAttemptAt = null;
        this.updatedAt = now;
    }

    public void markFailed(String failureCode, LocalDateTime now) {
        this.status = ChargeOrderStatus.FAILED;
        this.failureCode = failureCode;
        this.recoveryNextAttemptAt = null;
        this.updatedAt = now;
    }

    public void scheduleNextRecovery(LocalDateTime nextAttemptAt) {
        this.recoveryNextAttemptAt = nextAttemptAt;
    }

    public void markRecoveryExhausted(LocalDateTime now) {
        this.recoveryExhaustedAt = now;
        this.recoveryNextAttemptAt = null;
    }
}
