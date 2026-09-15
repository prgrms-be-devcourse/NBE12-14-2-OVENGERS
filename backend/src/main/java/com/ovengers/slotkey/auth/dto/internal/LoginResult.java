package com.ovengers.slotkey.auth.dto.internal;

public record LoginResult(
        String accessToken,
        String refreshToken
) {
}