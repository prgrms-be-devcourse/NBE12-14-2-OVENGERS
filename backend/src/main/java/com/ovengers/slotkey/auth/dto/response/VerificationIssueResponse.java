package com.ovengers.slotkey.auth.dto.response;

import java.time.LocalDateTime;

public record VerificationIssueResponse(
        Long challengeId,
        LocalDateTime expiresAt,
        LocalDateTime resendAvailableAt
) {
}