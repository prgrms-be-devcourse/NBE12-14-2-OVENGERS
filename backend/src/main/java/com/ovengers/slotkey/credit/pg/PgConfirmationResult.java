package com.ovengers.slotkey.credit.pg;

public record PgConfirmationResult(
        Type type,
        String paymentKey,
        String errorCode,
        UnknownReason unknownReason,
        String paymentMethod,
        String paymentProvider
) {

    public enum Type {
        APPROVED,
        DECLINED,
        UNKNOWN
    }

    public enum UnknownReason {
        ALREADY_PROCESSED,
        MERCHANT_ERROR,
        INDETERMINATE
    }

    public static PgConfirmationResult approved(String paymentKey) {
        return approved(paymentKey, null, null);
    }

    /** 결제수단·간편결제 제공사는 표시용이며 PG 응답에 없으면 null이다. */
    public static PgConfirmationResult approved(String paymentKey, String paymentMethod, String paymentProvider) {
        return new PgConfirmationResult(Type.APPROVED, paymentKey, null, null, paymentMethod, paymentProvider);
    }

    public static PgConfirmationResult declined(String errorCode) {
        return new PgConfirmationResult(Type.DECLINED, null, errorCode, null, null, null);
    }

    public static PgConfirmationResult unknown(
            String errorCode,
            UnknownReason reason
    ) {
        return new PgConfirmationResult(Type.UNKNOWN, null, errorCode, reason, null, null);
    }
}
