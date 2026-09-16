package com.ovengers.slotkey.reservation.dto.response;

import com.ovengers.slotkey.access.entity.AccessDenyReason;
import com.ovengers.slotkey.access.entity.AccessResult;
import com.ovengers.slotkey.access.entity.DoorAccessLog;
import java.time.LocalDateTime;

/**
 * 출입 시도 이력 응답 (관리자 예약 상세 조회 포함용).
 */
public record DoorAccessLogResponse(
    Long accessLogId,
    LocalDateTime attemptedAt,
    AccessResult result,
    AccessDenyReason reasonCode
) {
    public static DoorAccessLogResponse from(DoorAccessLog log) {
        return new DoorAccessLogResponse(
            log.getId(),
            log.getAttemptedAt(),
            log.getResult(),
            log.getReasonCode()
        );
    }
}
