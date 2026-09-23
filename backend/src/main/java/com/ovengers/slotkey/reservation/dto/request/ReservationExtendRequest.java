package com.ovengers.slotkey.reservation.dto.request;

import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 연장 요청. expectedEndTime은 클라이언트가 알고 있는 현재 종료 시각(낙관적 검사용, core-domain-decisions 7-2). */
@Schema(description = "예약 이용 시간 연장 요청")
public record ReservationExtendRequest(

        @Schema(
                description = "조회한 예약의 기존 종료 시각. 다른 요청에 의해 변경되었는지 확인하는 기준입니다.",
                example = "2026-10-01T10:00:00"
        )
        @NotNull LocalDateTime expectedEndTime,

        @Schema(
                description = "연장할 종료 시각. 기존 종료 시각보다 늦어야 하며 30분 단위로 지정합니다.",
                example = "2026-10-01T10:30:00"
        )
        @NotNull LocalDateTime newEndTime
) {
}