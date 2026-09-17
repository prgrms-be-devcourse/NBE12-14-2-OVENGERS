package com.ovengers.slotkey.space.service;

import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.space.dto.response.SlotResponse;
import com.ovengers.slotkey.space.dto.response.SpaceSlotAvailabilityResponse;
import com.ovengers.slotkey.space.entity.Space;
import com.ovengers.slotkey.space.entity.SpaceStatus;
import com.ovengers.slotkey.space.policy.SpaceOperatingHoursPolicy;
import com.ovengers.slotkey.space.repository.SpaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SpaceSlotAvailabilityServiceTest {

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private OccupiedSlotProvider occupiedSlotProvider;

    private SpaceOperatingHoursPolicy operatingHoursPolicy;
    private SpaceSlotAvailabilityService availabilityService;

    // 고정 기준 시각: 2026-09-20T10:15:00 KST
    private final ZoneId zoneId = ZoneId.of("Asia/Seoul");
    private final LocalDateTime fixedNow = LocalDateTime.of(2026, 9, 20, 10, 15, 0);
    private final Clock clock = Clock.fixed(fixedNow.atZone(zoneId).toInstant(), zoneId);

    @BeforeEach
    void setUp() {
        operatingHoursPolicy = new SpaceOperatingHoursPolicy();
        availabilityService = new SpaceSlotAvailabilityService(
                spaceRepository,
                operatingHoursPolicy,
                occupiedSlotProvider,
                clock);
    }

    private Space createSpace(Long id, SpaceStatus status) {
        return Space.builder()
                .id(id)
                .name("회의실 A")
                .location("강남점 3층")
                .capacity(6)
                .pricePerSlot(5000L)
                .openingTime(LocalTime.of(9, 0))
                .closingTime(LocalTime.of(12, 0)) // 09:00 ~ 12:00 (총 6슬롯)
                .status(status)
                .build();
    }

    @Test
    @DisplayName("고정된 시각(10:15) 기준, 과거 슬롯(09:00, 09:30)은 비활성화되고 미래 슬롯(10:30~)은 활성화된다")
    void getSlotAvailability_pastSlotsDisabled() {
        // given
        Long spaceId = 1L;
        LocalDate targetDate = LocalDate.of(2026, 9, 20);
        Space space = createSpace(spaceId, SpaceStatus.ACTIVE);

        given(spaceRepository.findById(spaceId)).willReturn(Optional.of(space));
        given(occupiedSlotProvider.getOccupiedSlotStarts(spaceId, targetDate)).willReturn(Set.of());

        // when
        SpaceSlotAvailabilityResponse response = availabilityService.getSlotAvailability(spaceId, targetDate);

        // then
        List<SlotResponse> slots = response.slots();
        assertThat(slots).hasSize(6);

        // 09:00~09:30 -> 과거이므로 false
        assertThat(slots.get(0).slotStart()).isEqualTo(LocalDateTime.of(2026, 9, 20, 9, 0));
        assertThat(slots.get(0).isAvailable()).isFalse();

        // 09:30~10:00 -> 과거이므로 false
        assertThat(slots.get(1).slotStart()).isEqualTo(LocalDateTime.of(2026, 9, 20, 9, 30));
        assertThat(slots.get(1).isAvailable()).isFalse();

        // 10:00~10:30 -> start(10:00)가 now(10:15)보다 이전이므로 false
        assertThat(slots.get(2).slotStart()).isEqualTo(LocalDateTime.of(2026, 9, 20, 10, 0));
        assertThat(slots.get(2).isAvailable()).isFalse();

        // 10:30~11:00 -> 미래이므로 true
        assertThat(slots.get(3).slotStart()).isEqualTo(LocalDateTime.of(2026, 9, 20, 10, 30));
        assertThat(slots.get(3).isAvailable()).isTrue();

        // 11:00~11:30 -> 미래이므로 true
        assertThat(slots.get(4).slotStart()).isEqualTo(LocalDateTime.of(2026, 9, 20, 11, 0));
        assertThat(slots.get(4).isAvailable()).isTrue();
    }

    @Test
    @DisplayName("점유된 슬롯은 미래 시각이더라도 isAvailable이 false로 반환된다")
    void getSlotAvailability_occupiedSlotIsFalse() {
        // given
        Long spaceId = 1L;
        LocalDate targetDate = LocalDate.of(2026, 9, 20);
        Space space = createSpace(spaceId, SpaceStatus.ACTIVE);

        // 11:00 슬롯이 이미 점유됨
        LocalDateTime occupiedTime = LocalDateTime.of(2026, 9, 20, 11, 0);
        given(spaceRepository.findById(spaceId)).willReturn(Optional.of(space));
        given(occupiedSlotProvider.getOccupiedSlotStarts(spaceId, targetDate)).willReturn(Set.of(occupiedTime));

        // when
        SpaceSlotAvailabilityResponse response = availabilityService.getSlotAvailability(spaceId, targetDate);

        // then
        List<SlotResponse> slots = response.slots();
        // 10:30 슬롯 -> 점유 안 됨 -> true
        assertThat(slots.get(3).isAvailable()).isTrue();
        // 11:00 슬롯 -> 점유됨 -> false
        assertThat(slots.get(4).isAvailable()).isFalse();
        // 11:30 슬롯 -> 점유 안 됨 -> true
        assertThat(slots.get(5).isAvailable()).isTrue();
    }

    @Test
    @DisplayName("공간 상태가 INACTIVE이면 점유 데이터가 존재하더라도 모든 슬롯이 false로 반환된다")
    void getSlotAvailability_inactiveSpaceWithOccupiedSlots_allSlotsFalse() {
        // given
        Long spaceId = 1L;
        LocalDate targetDate = LocalDate.of(2026, 9, 20);
        Space inactiveSpace = createSpace(spaceId, SpaceStatus.INACTIVE);

        LocalDateTime occupiedTime = LocalDateTime.of(2026, 9, 20, 11, 0);
        given(spaceRepository.findById(spaceId)).willReturn(Optional.of(inactiveSpace));
        given(occupiedSlotProvider.getOccupiedSlotStarts(spaceId, targetDate)).willReturn(Set.of(occupiedTime));

        // when
        SpaceSlotAvailabilityResponse response = availabilityService.getSlotAvailability(spaceId, targetDate);

        // then
        assertThat(response.slots()).allMatch(slot -> !slot.isAvailable());
    }

    @Test
    @DisplayName("요청된 spaceId와 date가 응답 DTO에 정확히 보존된다")
    void getSlotAvailability_preservesSpaceIdAndDate() {
        // given
        Long spaceId = 42L;
        LocalDate targetDate = LocalDate.of(2026, 9, 20);
        Space space = createSpace(spaceId, SpaceStatus.ACTIVE);

        given(spaceRepository.findById(spaceId)).willReturn(Optional.of(space));
        given(occupiedSlotProvider.getOccupiedSlotStarts(spaceId, targetDate)).willReturn(Set.of());

        // when
        SpaceSlotAvailabilityResponse response = availabilityService.getSlotAvailability(spaceId, targetDate);

        // then
        assertThat(response.spaceId()).isEqualTo(42L);
        assertThat(response.date()).isEqualTo(targetDate);
    }

    @Test
    @DisplayName("존재하지 않는 spaceId 조회 시 SPACE_NOT_FOUND 예외를 던지고 occupiedSlotProvider를 호출하지 않는다")
    void getSlotAvailability_spaceNotFound_throwsException() {
        // given
        Long invalidId = 999L;
        given(spaceRepository.findById(invalidId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> availabilityService.getSlotAvailability(invalidId, LocalDate.of(2026, 9, 20)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SPACE_NOT_FOUND);

        org.mockito.Mockito.verifyNoInteractions(occupiedSlotProvider);
    }

    @Test
    @DisplayName("현재 시각과 슬롯 시작 시각이 정확히 일치할 때 isBefore는 false이므로 슬롯이 가용한 상태(true)로 판단된다 (현재 구현 문서화)")
    void getSlotAvailability_exactStartTimeMatch_isAvailableUnderCurrentImplementation() {
        // given
        Long spaceId = 1L;
        LocalDate targetDate = LocalDate.of(2026, 9, 20);
        Space space = createSpace(spaceId, SpaceStatus.ACTIVE);

        // 현재 시각을 10:30:00 정각으로 고정한 Clock 준비
        LocalDateTime exactNow = LocalDateTime.of(2026, 9, 20, 10, 30, 0);
        Clock exactClock = Clock.fixed(exactNow.atZone(zoneId).toInstant(), zoneId);
        SpaceSlotAvailabilityService serviceWithExactClock = new SpaceSlotAvailabilityService(
                spaceRepository,
                operatingHoursPolicy,
                occupiedSlotProvider,
                exactClock);

        given(spaceRepository.findById(spaceId)).willReturn(Optional.of(space));
        given(occupiedSlotProvider.getOccupiedSlotStarts(spaceId, targetDate)).willReturn(Set.of());

        // when
        SpaceSlotAvailabilityResponse response = serviceWithExactClock.getSlotAvailability(spaceId, targetDate);

        // then
        // slots(3)은 10:30~11:00 슬롯. window.start() == 10:30, now == 10:30 ->
        // isBefore(now)는 false -> isPast == false -> available == true
        SlotResponse slot1030 = response.slots().get(3);
        assertThat(slot1030.slotStart()).isEqualTo(LocalDateTime.of(2026, 9, 20, 10, 30));
        assertThat(slot1030.isAvailable()).isTrue();
    }
}
