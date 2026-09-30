package com.ovengers.slotkey.reservation.dto.request;

import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 관리자 예약 검색 조건 DTO.
 * 모든 조건은 선택 사항이며, 빈 값은 미선택으로 처리됩니다.
 */
@Schema(description = "관리자 예약 검색 조건. 모든 조건은 선택 사항입니다.")
public record AdminReservationSearchCondition(
        @Schema(description = "이용 시작 날짜 (YYYY-MM-DD)", example = "2026-09-30")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate date,

        @Schema(description = "공간 ID (양수)", example = "1")
        @Positive(message = "공간 ID는 양수여야 합니다.")
        Long spaceId,

        @Schema(description = "예약 상태")
        ReservationStatus status
) {
    public AdminReservationSearchCondition {
        // spaceId, date, status 는 record 바인딩 시 null 허용
    }
}
