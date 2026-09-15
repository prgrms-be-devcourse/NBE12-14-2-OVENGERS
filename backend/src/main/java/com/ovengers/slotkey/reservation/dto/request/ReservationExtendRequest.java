package com.ovengers.slotkey.reservation.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** 연장 요청. expectedEndTime은 클라이언트가 알고 있는 현재 종료 시각(낙관적 검사용, §7-2). */
public record ReservationExtendRequest(
        @NotNull LocalDateTime expectedEndTime,
        @NotNull LocalDateTime newEndTime
) {
}
