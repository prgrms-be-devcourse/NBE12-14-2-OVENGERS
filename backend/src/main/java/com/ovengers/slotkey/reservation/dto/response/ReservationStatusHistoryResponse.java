package com.ovengers.slotkey.reservation.dto.response;

import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import com.ovengers.slotkey.reservation.entity.ReservationStatusHistory;

import java.time.LocalDateTime;

public record ReservationStatusHistoryResponse(
        ReservationStatus fromStatus,
        ReservationStatus toStatus,
        String reason,
        LocalDateTime changedAt,
        Long changedBy

) {

    public static ReservationStatusHistoryResponse from(
            ReservationStatusHistory history
    ) {
        Long changedBy = null;

        // 회원이 상태를 변경한 경우 회원ID를 저장
        if (history.getChangedByMember() != null) {
            changedBy = history.getChangedByMember().getId();
        }

        return new ReservationStatusHistoryResponse(
                history.getFromStatus(),
                history.getToStatus(),
                history.getReason(),
                history.getChangedAt(),
                changedBy
        );
    }
}