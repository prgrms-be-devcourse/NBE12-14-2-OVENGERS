package com.ovengers.slotkey.reservation.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PricingServiceTest {

    private final PricingService pricingService = new PricingService();

    @Test
    @DisplayName("2시간 구간은 30분 슬롯 4개로 계산된다")
    void calculateSlotCount_twoHours_returnsFourSlots() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 17, 14, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 17, 16, 0);

        int slotCount = pricingService.calculateSlotCount(start, end);

        assertThat(slotCount).isEqualTo(4);
    }

    @Test
    @DisplayName("30분 구간은 슬롯 1개로 계산된다")
    void calculateSlotCount_thirtyMinutes_returnsOneSlot() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 17, 14, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 17, 14, 30);

        int slotCount = pricingService.calculateSlotCount(start, end);

        assertThat(slotCount).isEqualTo(1);
    }

    @Test
    @DisplayName("30분 배수가 아닌 구간은 남는 시간을 버리고 내림 계산한다")
    void calculateSlotCount_notSlotAligned_flooredDown() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 17, 14, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 17, 14, 45);

        int slotCount = pricingService.calculateSlotCount(start, end);

        assertThat(slotCount).isEqualTo(1);
    }

    @Test
    @DisplayName("슬롯 단가와 슬롯 수를 곱해 총액을 계산한다")
    void calculateTotalAmount_multipliesPriceAndSlotCount() {
        int totalAmount = pricingService.calculateTotalAmount(5000, 4);

        assertThat(totalAmount).isEqualTo(20000);
    }

    @Test
    @DisplayName("슬롯 수가 0이면 총액도 0이다")
    void calculateTotalAmount_zeroSlots_returnsZero() {
        int totalAmount = pricingService.calculateTotalAmount(5000, 0);

        assertThat(totalAmount).isEqualTo(0);
    }
}
