package com.ovengers.slotkey.reservation.dto.response;

import com.ovengers.slotkey.reservation.entity.Reservation;
import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import java.time.LocalDateTime;

/**
 * 관리자 예약 목록 조회 응답 (core-domain-decisions 3-4).
 */
public record AdminReservationResponse(
        Long reservationId,
        Long memberId,
        String memberEmail,
        Long spaceId,
        String spaceName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        ReservationStatus status,
        Long totalAmount,
        LocalDateTime createdAt
) {
    public static AdminReservationResponse from(Reservation reservation) {
        return new AdminReservationResponse(
                reservation.getId(),
                reservation.getMemberId(),
                reservation.getMember().getEmail(),
                reservation.getSpaceId(),
                reservation.getSpace().getName(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getStatus(),
                reservation.getTotalAmount(),
                reservation.getCreatedAt()
        );
    }
}