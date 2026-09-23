package com.ovengers.slotkey.reservation.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import io.swagger.v3.oas.annotations.media.Schema;

/** 예약(HOLD) 생성 요청. 결제는 이 요청에 포함되지 않는다(core-domain-decisions 2-1). */
@Schema(description = "결제 전 슬롯 임시 확보 요청")
public record ReservationCreateRequest(

        @Schema(description = "예약할 공간 ID", example = "1")
        @NotNull Long spaceId,

        @Schema(description = "이용 날짜", example = "2026-10-01")
        @NotNull LocalDate date,

        @Schema(description = "이용 시작 시각 (30분 단위)", example = "09:00:00", type = "string")
        @NotNull LocalTime startTime,

        @Schema(description = "같은 날짜의 이용 종료 시각 (30분 단위)", example = "10:00:00", type = "string")
        @NotNull LocalTime endTime
) {
    public LocalDateTime toStartDateTime() {
        return LocalDateTime.of(date, startTime);
    }

    public LocalDateTime toEndDateTime() {
        return LocalDateTime.of(date, endTime);
    }
}
