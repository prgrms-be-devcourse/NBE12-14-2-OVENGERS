package com.ovengers.slotkey.credit.pg;

public record PgPaymentLookupResult(Status status, String paymentKey) {

    public enum Status {
        APPROVED,
        FAILED,
        UNKNOWN
    }

    public static PgPaymentLookupResult approved(String paymentKey) {
        return new PgPaymentLookupResult(Status.APPROVED, paymentKey);
    }

    public static PgPaymentLookupResult failed(String paymentKey) {
        return new PgPaymentLookupResult(Status.FAILED, paymentKey);
    }

    public static PgPaymentLookupResult unknown() {
        return new PgPaymentLookupResult(Status.UNKNOWN, null);
    }
}
