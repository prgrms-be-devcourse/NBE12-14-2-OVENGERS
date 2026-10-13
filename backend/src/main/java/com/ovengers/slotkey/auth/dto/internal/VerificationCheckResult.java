package com.ovengers.slotkey.auth.dto.internal;

import java.time.LocalDateTime;

public record VerificationCheckResult(
        Status status,
        String grant,
        LocalDateTime expiresAt,
        LocalDateTime blockedUntil
) {
    public enum Status {
        SUCCESS,
        WRONG_CODE,
        INVALID_CHALLENGE,
        TEMPORARILY_BLOCKED,
        ADMIN_LOCKED
    }

    public static VerificationCheckResult failure(
            Status status,
            LocalDateTime blockedUntil
    ) {
        return new VerificationCheckResult(
                status, null, null, blockedUntil
        );
    }
}