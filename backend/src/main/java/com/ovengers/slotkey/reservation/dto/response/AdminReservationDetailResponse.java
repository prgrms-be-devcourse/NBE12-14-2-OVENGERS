package com.ovengers.slotkey.reservation.dto.response;

import com.ovengers.slotkey.reservation.entity.Reservation;
import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 예약 상세 조회 응답 (출입 이력 포함, core-domain-decisions 3-4).
 */
public record AdminReservationDetailResponse(
    Long reservationId,
    Long memberId,
    String memberEmail,
    Long spaceId,
    String spaceName,
    LocalDateTime startTime,
    LocalDateTime endTime,
    ReservationStatus status,
    Long totalAmount,
    Long pricePerSlotSnapshot,
    LocalDateTime createdAt,
    LocalDateTime cancelledAt,
    LocalDateTime checkedInAt,
    LocalDateTime checkedOutAt,
    List<ReservationStatusHistoryResponse> statusHistory,
    List<DoorAccessLogResponse> accessLogs
) {
    public static AdminReservationDetailResponse from(
        Reservation reservation,
        List<ReservationStatusHistoryResponse> statusHistory,
        List<DoorAccessLogResponse> accessLogs
    ) {
        return new AdminReservationDetailResponse(
            reservation.getId(),
            reservation.getMemberId(),
            reservation.getMember().getEmail(),
            reservation.getSpaceId(),
            reservation.getSpace().getName(),
            reservation.getStartTime(),
            reservation.getEndTime(),
            reservation.getStatus(),
            reservation.getTotalAmount(),
            reservation.getPricePerSlotSnapshot(),
            reservation.getCreatedAt(),
            reservation.getCancelledAt(),
            reservation.getCheckedInAt(),
            reservation.getCheckedOutAt(),
            statusHistory,
            accessLogs
        );
    }
}