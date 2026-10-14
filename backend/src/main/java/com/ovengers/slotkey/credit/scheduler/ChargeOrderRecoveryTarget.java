package com.ovengers.slotkey.credit.scheduler;

public record ChargeOrderRecoveryTarget(
        Long chargeOrderId,
        String orderId,
        String paymentKey,
        String clientIdempotencyKey,
        long amount
) {
}
