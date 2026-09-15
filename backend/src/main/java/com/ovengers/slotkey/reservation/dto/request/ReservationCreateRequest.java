package com.ovengers.slotkey.reservation.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 예약(HOLD) 생성 요청. 결제는 이 요청에 포함되지 않는다(§2-1). */
public record ReservationCreateRequest(
        @NotNull Long spaceId,
        @NotNull LocalDate date,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime
) {
    public LocalDateTime toStartDateTime() {
        return LocalDateTime.of(date, startTime);
    }

    public LocalDateTime toEndDateTime() {
        return LocalDateTime.of(date, endTime);
    }
}
