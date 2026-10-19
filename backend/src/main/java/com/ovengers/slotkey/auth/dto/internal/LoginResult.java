package com.ovengers.slotkey.auth.dto.internal;

import java.time.LocalDateTime;

public record LoginResult(
        String accessToken,
        String refreshToken,
        LocalDateTime sessionExpiresAt
) {
}
