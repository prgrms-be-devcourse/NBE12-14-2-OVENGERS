package com.ovengers.slotkey.access.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class DoorAccessTokenResponse {

    private Long reservationId;
    private String token;
    private LocalDateTime issuedAt;

    // 출입 토큰 발급 응답 생성
    public static DoorAccessTokenResponse of(
            Long reservationId,
            String token,
            LocalDateTime issuedAt
    ) {
        return new DoorAccessTokenResponse(
                reservationId,
                token,
                issuedAt
        );
    }
}
