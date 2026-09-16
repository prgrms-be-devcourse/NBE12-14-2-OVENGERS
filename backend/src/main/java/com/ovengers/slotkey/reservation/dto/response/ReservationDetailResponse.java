package com.ovengers.slotkey.reservation.dto.response;

import com.ovengers.slotkey.reservation.entity.Reservation;
import com.ovengers.slotkey.reservation.entity.ReservationStatusHistory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 본인 예약 상세 조회 응답(GET /reservations/{id}).
 * 목록 응답(ReservationResponse)에 상태 전이 이력(statusHistory)을 더한 형태다.
 */
public record ReservationDetailResponse(
        Long reservationId,
        Long spaceId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status,
        int pricePerSlotSnapshot,
        int totalAmount,
        LocalDateTime holdExpiresAt,
        LocalDateTime checkedInAt,
        LocalDateTime checkedOutAt,
        LocalDateTime cancelledAt,
        LocalDateTime createdAt,
        List<ReservationStatusHistoryResponse> statusHistory
) {
    public static ReservationDetailResponse of(Reservation reservation, List<ReservationStatusHistory> histories) {
        return new ReservationDetailResponse(
                reservation.getId(),
                reservation.getSpaceId(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getStatus().name(),
                reservation.getPricePerSlotSnapshot(),
                reservation.getTotalAmount(),
                reservation.getHoldExpiresAt(),
                reservation.getCheckedInAt(),
                reservation.getCheckedOutAt(),
                reservation.getCancelledAt(),
                reservation.getCreatedAt(),
                histories.stream().map(ReservationStatusHistoryResponse::from).toList()
        );
    }
}