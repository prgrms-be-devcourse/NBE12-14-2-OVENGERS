package com.ovengers.slotkey.reservation.dto.response;

import com.ovengers.slotkey.reservation.entity.Reservation;
import com.ovengers.slotkey.space.entity.Space;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReservationListResponse(
        Long reservationId,
        Long spaceId,
        String spaceName,
        String spaceLocation,
        String spaceImagePath,
        LocalDate date,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status,
        int pricePerSlotSnapshot,
        int totalAmount,
        LocalDateTime holdExpiresAt,
        LocalDateTime checkedInAt,
        LocalDateTime checkedOutAt,
        LocalDateTime cancelledAt,
        LocalDateTime createdAt
) {
    public static ReservationListResponse from(
            Reservation reservation,
            Space space
    ) {
        return new ReservationListResponse(
                reservation.getId(),
                reservation.getSpaceId(),
                space == null ? "공간 정보 없음" : space.getName(),
                space == null ? null : space.getLocation(),
                space == null ? null : space.getImagePath(),
                reservation.getStartTime().toLocalDate(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getStatus().name(),
                reservation.getPricePerSlotSnapshot(),
                reservation.getTotalAmount(),
                reservation.getHoldExpiresAt(),
                reservation.getCheckedInAt(),
                reservation.getCheckedOutAt(),
                reservation.getCancelledAt(),
                reservation.getCreatedAt()
        );
    }
}