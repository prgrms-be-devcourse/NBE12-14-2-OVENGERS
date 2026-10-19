package com.ovengers.slotkey.auth.dto.internal;

import java.time.LocalDateTime;

public record IssuedRefreshToken(
        String rawToken,
        String sessionId,
        LocalDateTime sessionExpiresAt
) {
}
