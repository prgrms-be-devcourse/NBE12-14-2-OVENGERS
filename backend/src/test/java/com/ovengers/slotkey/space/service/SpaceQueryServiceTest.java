package com.ovengers.slotkey.space.service;

import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.space.dto.request.SpaceSearchCondition;
import com.ovengers.slotkey.space.entity.Space;
import com.ovengers.slotkey.space.entity.SpaceStatus;
import com.ovengers.slotkey.space.repository.SpaceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SpaceQueryServiceTest {

    private static final List<Long> NO_EXCLUSION = List.of(-1L);

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private OccupiedSlotProvider occupiedSlotProvider;

    @Mock
    private Clock clock;

    @InjectMocks
    private SpaceQueryService spaceQueryService;

    private Space createSpace(Long id, String name) {
        return Space.builder()
                .id(id)
                .name(name)
                .location("서울시 강남구")
                .description("설명")
                .capacity(6)
                .pricePerSlot(5000L)
                .openingTime(LocalTime.of(9, 0))
                .closingTime(LocalTime.of(18, 0))
                .status(SpaceStatus.ACTIVE)
                .version(0)
                .build();
    }

    private SpaceSearchCondition conditionOf(String keyword) {
        return new SpaceSearchCondition(keyword, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("keyword가 없으면 검색 조건 없이 searchSpaces를 호출한다")
    void getSpacesPage_blankKeyword_callsSearchSpaces() {
        // given
        Pageable pageable = PageRequest.of(0, 10);
        List<Space> spaces = List.of(createSpace(1L, "회의실 1"), createSpace(2L, "회의실 2"));
        Page<Space> expectedPage = new PageImpl<>(spaces, pageable, spaces.size());

        given(spaceRepository.searchSpaces(
                eq(SpaceStatus.ACTIVE), isNull(), isNull(), isNull(), isNull(), eq(NO_EXCLUSION), eq(pageable)))
                .willReturn(expectedPage);

        // when
        Page<Space> actualPage = spaceQueryService.getSpacesPage(pageable, conditionOf(null));

        // then
        assertThat(actualPage.getContent()).hasSize(2);
        verify(spaceRepository).searchSpaces(
                SpaceStatus.ACTIVE, null, null, null, null, NO_EXCLUSION, pageable);
    }

    @Test
    @DisplayName("유효한 keyword가 주어지면 searchSpaces에 그대로 전달한다")
    void getSpacesPage_validKeyword_callsSearchSpaces() {
        // given
        String keyword = "회의실";
        Pageable pageable = PageRequest.of(0, 10);
        List<Space> spaces = List.of(createSpace(1L, "회의실 1"));
        Page<Space> expectedPage = new PageImpl<>(spaces, pageable, spaces.size());

        given(spaceRepository.searchSpaces(
                eq(SpaceStatus.ACTIVE), eq(keyword), isNull(), isNull(), isNull(), eq(NO_EXCLUSION), eq(pageable)))
                .willReturn(expectedPage);

        // when
        Page<Space> actualPage = spaceQueryService.getSpacesPage(pageable, conditionOf(keyword));

        // then
        assertThat(actualPage.getContent()).hasSize(1);
        assertThat(actualPage.getContent().get(0).getName()).isEqualTo("회의실 1");
        verify(spaceRepository).searchSpaces(
                SpaceStatus.ACTIVE, keyword, null, null, null, NO_EXCLUSION, pageable);
    }

    @Test
    @DisplayName("시간대 필터를 지정하면 점유된 공간 id를 조회해 제외 목록으로 넘긴다")
    void getSpacesPage_withTimeFilter_excludesOccupiedSpaces() {
        // given
        Pageable pageable = PageRequest.of(0, 10);
        LocalDate date = LocalDate.of(2026, 9, 23);
        LocalTime startTime = LocalTime.of(14, 0);
        LocalTime endTime = LocalTime.of(15, 0);
        SpaceSearchCondition condition =
                new SpaceSearchCondition(null, "강남", null, null, date, startTime, endTime);

        Clock fixedClock = Clock.fixed(
                java.time.Instant.parse("2026-09-23T00:00:00Z"), java.time.ZoneId.of("Asia/Seoul"));
        given(clock.getZone()).willReturn(fixedClock.getZone());
        given(clock.instant()).willReturn(fixedClock.instant());

        given(occupiedSlotProvider.getOccupiedSpaceIds(any(), any(), any()))
                .willReturn(java.util.Set.of(9L, 10L));

        List<Space> spaces = List.of(createSpace(1L, "회의실 1"));
        Page<Space> expectedPage = new PageImpl<>(spaces, pageable, spaces.size());
        given(spaceRepository.searchSpaces(
                eq(SpaceStatus.ACTIVE), isNull(), eq("강남"), isNull(), isNull(), any(), eq(pageable)))
                .willReturn(expectedPage);

        // when
        Page<Space> actualPage = spaceQueryService.getSpacesPage(pageable, condition);

        // then
        assertThat(actualPage.getContent()).hasSize(1);
        verify(spaceRepository).searchSpaces(
                eq(SpaceStatus.ACTIVE), isNull(), eq("강남"), isNull(), isNull(),
                argThatContainsExactlyInAnyOrder(9L, 10L), eq(pageable));
    }

    private List<Long> argThatContainsExactlyInAnyOrder(Long... ids) {
        return org.mockito.ArgumentMatchers.argThat(list ->
                list != null && list.size() == ids.length && list.containsAll(List.of(ids)));
    }

    @Test
    @DisplayName("시작 시각이 종료 시각보다 늦으면 INVALID_TIME_RANGE 예외가 발생한다")
    void getSpacesPage_invalidTimeRange_throwsException() {
        // given
        Pageable pageable = PageRequest.of(0, 10);
        LocalDate date = LocalDate.of(2026, 9, 23);
        SpaceSearchCondition condition =
                new SpaceSearchCondition(null, null, null, null, date, LocalTime.of(15, 0), LocalTime.of(14, 0));

        // when & then
        assertThatThrownBy(() -> spaceQueryService.getSpacesPage(pageable, condition))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TIME_RANGE);

        verify(spaceRepository, never()).searchSpaces(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("존재하는 공간 ID로 상세 조회 시 공간 엔티티를 반환한다")
    void getSpaceDetailById_success() {
        // given
        Long spaceId = 1L;
        Space space = createSpace(spaceId, "회의실 A");
        given(spaceRepository.findById(spaceId)).willReturn(Optional.of(space));

        // when
        Space result = spaceQueryService.getSpaceDetailById(spaceId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(spaceId);
        assertThat(result.getName()).isEqualTo("회의실 A");
        verify(spaceRepository).findById(spaceId);
    }

    @Test
    @DisplayName("존재하지 않는 공간 ID로 상세 조회 시 SPACE_NOT_FOUND 예외가 발생한다")
    void getSpaceDetailById_notFound_throwsException() {
        // given
        Long notFoundId = 999L;
        given(spaceRepository.findById(notFoundId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> spaceQueryService.getSpaceDetailById(notFoundId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SPACE_NOT_FOUND);

        verify(spaceRepository).findById(notFoundId);
    }
}
