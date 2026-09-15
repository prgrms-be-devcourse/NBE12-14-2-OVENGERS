package com.ovengers.slotkey.space.policy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpaceOperatingHoursPolicyTest {

    private SpaceOperatingHoursPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new SpaceOperatingHoursPolicy();
    }

    @Test
    @DisplayName("09:00부터 12:00까지 30분 단위 슬롯을 정상적으로 생성한다 (총 6개)")
    void generateSlots_success() {
        // given
        LocalDate date = LocalDate.of(2026, 9, 20);
        LocalTime openingTime = LocalTime.of(9, 0);
        LocalTime closingTime = LocalTime.of(12, 0);

        // when
        List<SpaceOperatingHoursPolicy.SlotWindow> slots = policy.generateSlots(date, openingTime, closingTime);

        // then
        assertThat(slots).hasSize(6);
        assertThat(slots.get(0).start()).isEqualTo(LocalDateTime.of(2026, 9, 20, 9, 0));
        assertThat(slots.get(0).end()).isEqualTo(LocalDateTime.of(2026, 9, 20, 9, 30));
        assertThat(slots.get(5).start()).isEqualTo(LocalDateTime.of(2026, 9, 20, 11, 30));
        assertThat(slots.get(5).end()).isEqualTo(LocalDateTime.of(2026, 9, 20, 12, 0));
    }

    @Test
    @DisplayName("시작 시각이 종료 시각과 같거나 늦으면 빈 목록을 반환한다")
    void generateSlots_invalidHours_returnsEmptyList() {
        // given
        LocalDate date = LocalDate.of(2026, 9, 20);

        // when & then
        assertThat(policy.generateSlots(date, LocalTime.of(18, 0), LocalTime.of(9, 0))).isEmpty();
        assertThat(policy.generateSlots(date, LocalTime.of(9, 0), LocalTime.of(9, 0))).isEmpty();
        assertThat(policy.generateSlots(date, null, LocalTime.of(18, 0))).isEmpty();
    }

    @Test
    @DisplayName("운영시간 내 포함 여부를 올바르게 검증한다")
    void isWithinOperatingHours() {
        LocalTime opening = LocalTime.of(9, 0);
        LocalTime closing = LocalTime.of(22, 0);

        // 정상 범위
        assertThat(policy.isWithinOperatingHours(opening, closing, LocalTime.of(9, 0), LocalTime.of(10, 0))).isTrue();
        assertThat(policy.isWithinOperatingHours(opening, closing, LocalTime.of(21, 30), LocalTime.of(22, 0))).isTrue();

        // 운영시간 이전 또는 이후 침범
        assertThat(policy.isWithinOperatingHours(opening, closing, LocalTime.of(8, 30), LocalTime.of(9, 30))).isFalse();
        assertThat(policy.isWithinOperatingHours(opening, closing, LocalTime.of(21, 0), LocalTime.of(22, 30)))
                .isFalse();

        // 시작이 종료보다 늦은 경우
        assertThat(policy.isWithinOperatingHours(opening, closing, LocalTime.of(15, 0), LocalTime.of(14, 0))).isFalse();
    }

    @Test
    @DisplayName("30분 단위 배수 여부를 정확히 판단한다")
    void isMultipleOf30Minutes() {
        assertThat(policy.isMultipleOf30Minutes(LocalTime.of(9, 0))).isTrue();
        assertThat(policy.isMultipleOf30Minutes(LocalTime.of(9, 30))).isTrue();
        assertThat(policy.isMultipleOf30Minutes(LocalTime.of(0, 0))).isTrue();

        assertThat(policy.isMultipleOf30Minutes(LocalTime.of(9, 15))).isFalse();
        assertThat(policy.isMultipleOf30Minutes(LocalTime.of(9, 30, 1))).isFalse();
        assertThat(policy.isMultipleOf30Minutes(null)).isFalse();
    }
}
