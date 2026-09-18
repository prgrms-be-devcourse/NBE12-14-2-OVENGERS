package com.ovengers.slotkey.access.dto.response;

import com.ovengers.slotkey.access.entity.AccessDenyReason;
import com.ovengers.slotkey.access.entity.AccessResult;
import com.ovengers.slotkey.access.entity.DoorAccessLog;

import java.time.LocalDateTime;

public record DoorAccessLogResponse(
        Long accessLogId,
        LocalDateTime attemptedAt,
        String requestedSpaceName,
        AccessResult result,
        AccessDenyReason reasonCode
) {
    public static DoorAccessLogResponse from(DoorAccessLog log) {
        return new DoorAccessLogResponse(
                log.getId(),
                log.getAttemptedAt(),
                log.getRequestedSpace() == null
                        ? null
                        : log.getRequestedSpace().getName(),
                log.getResult(),
                log.getReasonCode()
        );
    }
}