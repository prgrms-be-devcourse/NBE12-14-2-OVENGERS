package com.ovengers.slotkey.access.dto.response;

import com.ovengers.slotkey.access.entity.AccessDenyReason;
import com.ovengers.slotkey.access.entity.AccessResult;
import com.ovengers.slotkey.access.entity.DoorAccessLog;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class DoorAccessLogResponse {

    private Long accessLogId;
    private AccessResult result;
    private AccessDenyReason reasonCode;
    private LocalDateTime attemptedAt;

    // 출입 로그 조회 응답 생성
    public static DoorAccessLogResponse from(DoorAccessLog accessLog) {
        return new DoorAccessLogResponse(
                accessLog.getId(),
                accessLog.getResult(),
                accessLog.getReasonCode(),
                accessLog.getAttemptedAt()
        );
    }
}