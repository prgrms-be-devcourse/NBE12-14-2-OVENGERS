package com.ovengers.slotkey.credit.pg;

public record PgPaymentLookupResult(
        Status status,
        String paymentKey,
        String orderId,
        Long totalAmount,
        String currency,
        String rawStatus,
        boolean paymentObjectReceived
) {

    public enum Status {
        APPROVED,
        FAILED,
        UNKNOWN
    }

    public static PgPaymentLookupResult approved(String paymentKey) {
        return new PgPaymentLookupResult(
                Status.APPROVED,
                paymentKey,
                null,
                null,
                null,
                "DONE",
                true
        );
    }

    public static PgPaymentLookupResult approved(
            String paymentKey,
            String orderId,
            Long totalAmount,
            String currency,
            String rawStatus
    ) {
        return new PgPaymentLookupResult(
                Status.APPROVED,
                paymentKey,
                orderId,
                totalAmount,
                currency,
                rawStatus,
                true
        );
    }

    public static PgPaymentLookupResult failed(String paymentKey) {
        return new PgPaymentLookupResult(
                Status.FAILED,
                paymentKey,
                null,
                null,
                null,
                null,
                true
        );
    }

    public static PgPaymentLookupResult failed(
            String paymentKey,
            String orderId,
            Long totalAmount,
            String currency,
            String rawStatus
    ) {
        return new PgPaymentLookupResult(
                Status.FAILED,
                paymentKey,
                orderId,
                totalAmount,
                currency,
                rawStatus,
                true
        );
    }

    public static PgPaymentLookupResult unknown() {
        return new PgPaymentLookupResult(
                Status.UNKNOWN,
                null,
                null,
                null,
                null,
                null,
                false
        );
    }

    public static PgPaymentLookupResult unknown(
            String paymentKey,
            String orderId,
            Long totalAmount,
            String currency,
            String rawStatus
    ) {
        return new PgPaymentLookupResult(
                Status.UNKNOWN,
                paymentKey,
                orderId,
                totalAmount,
                currency,
                rawStatus,
                true
        );
    }
}
