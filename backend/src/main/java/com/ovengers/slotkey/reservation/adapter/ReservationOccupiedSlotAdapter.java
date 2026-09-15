package com.ovengers.slotkey.reservation.adapter;

import com.ovengers.slotkey.reservation.entity.ReservationSlot;
import com.ovengers.slotkey.reservation.repository.ReservationSlotRepository;
import com.ovengers.slotkey.space.service.OccupiedSlotProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * space 도메인의 OccupiedSlotProvider 포트에 대한 실제 구현체(space/service/addedServices.md에서
 * 미리 정의해 둔 연동 가이드 그대로). 이 빈이 등록되면 DefaultOccupiedSlotProvider는
 * @ConditionalOnMissingBean에 의해 자동으로 비활성화된다.
 */
@Component("ReservationOccupiedSlotAdapter")
@RequiredArgsConstructor
public class ReservationOccupiedSlotAdapter implements OccupiedSlotProvider {

    private final ReservationSlotRepository reservationSlotRepository;

    @Override
    public Set<LocalDateTime> getOccupiedSlotStarts(Long spaceId, LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(23, 59, 59);

        List<ReservationSlot> slots = reservationSlotRepository
                .findAllBySpaceIdAndSlotStartBetween(spaceId, startOfDay, endOfDay);

        return slots.stream()
                .map(ReservationSlot::getSlotStart)
                .collect(Collectors.toSet());
    }
}
