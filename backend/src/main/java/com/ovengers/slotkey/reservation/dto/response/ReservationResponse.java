package com.ovengers.slotkey.reservation.dto.response;

import com.ovengers.slotkey.reservation.entity.Reservation;

import java.time.LocalDateTime;

/**
 * HOLD 생성 / 결제 확인 / 연장 / 체크아웃 공통 응답 형태.
 * spaceVersion은 HOLD 생성 응답에서만 의미가 있다 — 클라이언트가 결제 확인 요청 시
 * 그대로 되돌려 보내면 서버가 space.version과 비교한다(§5-2). 그 외 응답에서는 null.
 */
public record ReservationResponse(
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
        Integer spaceVersion,
        LocalDateTime createdAt
) {
    public static ReservationResponse from(Reservation reservation) {
        return from(reservation, null);
    }

    public static ReservationResponse from(Reservation reservation, Integer spaceVersion) {
        return new ReservationResponse(
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
                spaceVersion,
                reservation.getCreatedAt()
        );
    }
}
