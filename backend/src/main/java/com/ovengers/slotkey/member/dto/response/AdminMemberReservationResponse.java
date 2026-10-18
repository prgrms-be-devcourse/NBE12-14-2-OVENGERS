
package com.ovengers.slotkey.member.dto.response;

import com.ovengers.slotkey.reservation.entity.Reservation;
import com.ovengers.slotkey.reservation.entity.ReservationStatus;

import java.time.LocalDateTime;

public record AdminMemberReservationResponse(
        Long reservationId,
        Long spaceId,
        String spaceName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        ReservationStatus status,
        Integer totalAmount,
        LocalDateTime createdAt
) {

    public static AdminMemberReservationResponse from(
            Reservation reservation,
            String spaceName
    ) {
        return new AdminMemberReservationResponse(
                reservation.getId(),
                reservation.getSpaceId(),
                spaceName,
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getStatus(),
                reservation.getTotalAmount(),
                reservation.getCreatedAt()
        );
    }
}
