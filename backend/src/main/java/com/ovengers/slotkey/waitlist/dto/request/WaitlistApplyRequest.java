package com.ovengers.slotkey.waitlist.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** 대기 신청 요청. 구간 검증(30분 정렬·미래·운영시간)은 기존 ReservationTimePolicy가 담당한다. */
public record WaitlistApplyRequest(
        @NotNull Long spaceId,
        @NotNull LocalDateTime startTime,
        @NotNull LocalDateTime endTime) {
}
