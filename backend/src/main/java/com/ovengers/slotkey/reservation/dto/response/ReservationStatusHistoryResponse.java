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
        return new ReservationStatusHistoryResponse(
                history.getFromStatus(),
                history.getToStatus(),
                history.getReason(),
                history.getChangedAt(),
                history.getChangedByMemberId()
        );
    }
}