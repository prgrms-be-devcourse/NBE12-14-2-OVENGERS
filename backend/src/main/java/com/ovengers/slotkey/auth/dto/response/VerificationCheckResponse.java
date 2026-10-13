package com.ovengers.slotkey.auth.dto.response;

import java.time.LocalDateTime;

public record VerificationCheckResponse(
        String grant,
        LocalDateTime expiresAt
) {
}