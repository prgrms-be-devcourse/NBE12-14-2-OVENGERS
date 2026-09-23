package com.ovengers.slotkey.space.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "30분 단위 공간 예약 슬롯")
public record SlotResponse(

        @Schema(
                description = "슬롯 시작 시각",
                example = "2026-10-01T09:00:00"
        )
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime slotStart,

        @Schema(
                description = "슬롯 종료 시각",
                example = "2026-10-01T09:30:00"
        )
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime slotEnd,

        @Schema(
                description = "조회 시점의 예약 가능 여부",
                example = "true"
        )
        boolean isAvailable
) {
    public static SlotResponse of(
            LocalDateTime slotStart,
            LocalDateTime slotEnd,
            boolean isAvailable
    ) {
        return new SlotResponse(slotStart, slotEnd, isAvailable);
    }
}