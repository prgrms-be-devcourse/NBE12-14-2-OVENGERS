package com.ovengers.slotkey.reservation.adapter;

import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import com.ovengers.slotkey.reservation.repository.ReservationSlotRepository;
import com.ovengers.slotkey.space.service.OccupiedSlotProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * space 도메인의 OccupiedSlotProvider 포트에 대한 실제 구현체.
 * ReservationSlot과 Reservation 상태를 함께 조회하여 유효 점유(CONFIRMED, IN_USE, COMPLETED 및
 * 유효한 HELD)만 반환한다.
 */
@Component("ReservationOccupiedSlotAdapter")
@RequiredArgsConstructor
public class ReservationOccupiedSlotAdapter implements OccupiedSlotProvider {

    private static final List<ReservationStatus> OCCUPIED_STATUSES = List.of(
            ReservationStatus.CONFIRMED,
            ReservationStatus.IN_USE,
            ReservationStatus.COMPLETED);

    private final ReservationSlotRepository reservationSlotRepository;

    @Override
    public Set<LocalDateTime> getOccupiedSlotStarts(Long spaceId, LocalDate date, LocalDateTime now) {
        LocalDateTime startInclusive = date.atStartOfDay();
        LocalDateTime endExclusive = date.plusDays(1).atStartOfDay();

        List<LocalDateTime> occupiedStarts = reservationSlotRepository.findOccupiedSlotStarts(
                spaceId,
                startInclusive,
                endExclusive,
                now,
                ReservationStatus.HELD,
                OCCUPIED_STATUSES);

        return new HashSet<>(occupiedStarts);
    }
}
