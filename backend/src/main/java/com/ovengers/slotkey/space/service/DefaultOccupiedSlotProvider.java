package com.ovengers.slotkey.space.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Set;

@Component
@ConditionalOnMissingBean(name = "ReservationOccupiedSlotAdapter")
public class DefaultOccupiedSlotProvider implements OccupiedSlotProvider {

    @Override
    public Set<LocalDateTime> getOccupiedSlotStarts(Long spaceId, LocalDate date, LocalDateTime now) {
        // 예약 도메인 연계 전 기본 구현: 점유된 슬롯 없음(빈 Set)
        return Collections.emptySet();
    }

    @Override
    public boolean hasOccupiedSlotsOutsideHours(Long spaceId, java.time.LocalTime openTime,
            java.time.LocalTime closeTime, LocalDateTime fromTime) {
        return false;
    }

    @Override
    public java.util.Set<Long> getOccupiedSpaceIds(LocalDateTime startInclusive, LocalDateTime endExclusive,
            LocalDateTime now) {
        return Collections.emptySet();
    }
}

