package com.ovengers.slotkey.reservation.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 가격 스냅샷 계산(core-domain-decisions 5-1). price_per_slot_snapshot × 점유 슬롯 수 = total_amount.
 * 여기서 계산한 값을 그대로 저장하며, 이후 공간 가격이 바뀌어도 기존 예약 금액은 불변이다.
 */
@Service
public class PricingService {

    private static final int SLOT_MINUTES = 30;

    /** [start, end) 구간의 30분 슬롯 개수. HoldService/ExtendService가 시간 구간을 넘길 때 사용한다. */
    public int calculateSlotCount(LocalDateTime start, LocalDateTime end) {
        long minutes = Duration.between(start, end).toMinutes();
        return (int) (minutes / SLOT_MINUTES);
    }

    public int calculateTotalAmount(int pricePerSlotSnapshot, int slotCount) {
        return pricePerSlotSnapshot * slotCount;
    }
}
