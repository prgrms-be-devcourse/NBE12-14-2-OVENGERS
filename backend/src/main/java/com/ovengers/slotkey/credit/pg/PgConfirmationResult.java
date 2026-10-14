package com.ovengers.slotkey.credit.pg;

public record PgConfirmationResult(
        Type type,
        String paymentKey,
        String errorCode,
        UnknownReason unknownReason
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
        return new PgConfirmationResult(Type.APPROVED, paymentKey, null, null);
    }

    public static PgConfirmationResult declined(String errorCode) {
        return new PgConfirmationResult(Type.DECLINED, null, errorCode, null);
    }

    public static PgConfirmationResult unknown(
            String errorCode,
            UnknownReason reason
    ) {
        return new PgConfirmationResult(Type.UNKNOWN, null, errorCode, reason);
    }
}
