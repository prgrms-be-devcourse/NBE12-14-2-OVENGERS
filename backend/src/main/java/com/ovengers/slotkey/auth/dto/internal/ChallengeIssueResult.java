package com.ovengers.slotkey.auth.dto.internal;

import java.time.LocalDateTime;

public record ChallengeIssueResult(
        Long challengeId,
        LocalDateTime expiresAt,
        LocalDateTime resendAvailableAt
) {
}