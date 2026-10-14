package com.ovengers.slotkey.credit.pg;

public record PgConfirmRequest(
        String paymentKey,
        String orderId,
        long amount,
        String idempotencyKey
) {
}
