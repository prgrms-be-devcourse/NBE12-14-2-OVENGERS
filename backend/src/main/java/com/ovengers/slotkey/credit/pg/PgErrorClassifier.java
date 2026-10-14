package com.ovengers.slotkey.credit.pg;

import java.util.Set;

public final class PgErrorClassifier {

    private static final Set<String> USER_DECLINE_CODES = Set.of(
            "REJECT_CARD_PAYMENT",
            "INVALID_REJECT_CARD",
            "REJECT_CARD_COMPANY",
            "REJECT_ACCOUNT_PAYMENT",
            "INVALID_STOPPED_CARD",
            "INVALID_CARD_NUMBER",
            "INVALID_CARD_EXPIRATION",
            "INVALID_CARD_LOST_OR_STOLEN",
            "INVALID_PASSWORD",
            "EXCEED_MAX_AUTH_COUNT",
            "EXCEED_MAX_AMOUNT",
            "EXCEED_MAX_DAILY_PAYMENT_COUNT",
            "EXCEED_MAX_PAYMENT_AMOUNT",
            "BELOW_MINIMUM_AMOUNT",
            "NOT_FOUND_PAYMENT_SESSION",
            "PAY_PROCESS_ABORTED",
            "PAY_PROCESS_CANCELED"
    );

    private static final Set<String> MERCHANT_ERROR_CODES = Set.of(
            "INVALID_API_KEY",
            "INVALID_CLIENT_KEY",
            "UNAUTHORIZED_KEY",
            "API_KEY_ACCESS_DENIED",
            "FORBIDDEN_REQUEST",
            "INVALID_REQUEST",
            "INVALID_PAYMENT_KEY"
    );

    private PgErrorClassifier() {
    }

    public static PgConfirmationResult classify(int httpStatus, String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            return PgConfirmationResult.unknown(
                    errorCode,
                    PgConfirmationResult.UnknownReason.INDETERMINATE
            );
        }
        if ("ALREADY_PROCESSED_PAYMENT".equals(errorCode)) {
            return PgConfirmationResult.unknown(
                    errorCode,
                    PgConfirmationResult.UnknownReason.ALREADY_PROCESSED
            );
        }
        if (httpStatus >= 500) {
            return PgConfirmationResult.unknown(
                    errorCode,
                    PgConfirmationResult.UnknownReason.INDETERMINATE
            );
        }
        if (USER_DECLINE_CODES.contains(errorCode)) {
            return PgConfirmationResult.declined(errorCode);
        }
        if (MERCHANT_ERROR_CODES.contains(errorCode)) {
            return PgConfirmationResult.unknown(
                    errorCode,
                    PgConfirmationResult.UnknownReason.MERCHANT_ERROR
            );
        }
        return PgConfirmationResult.unknown(
                errorCode,
                PgConfirmationResult.UnknownReason.INDETERMINATE
        );
    }
}
