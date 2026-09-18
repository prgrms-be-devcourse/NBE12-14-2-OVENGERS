package com.ovengers.slotkey.space.service;

import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.space.entity.Space;
import com.ovengers.slotkey.space.entity.SpaceStatus;
import com.ovengers.slotkey.space.repository.SpaceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SpaceQueryServiceTest {

    @Mock
    private SpaceRepository spaceRepository;

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

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   ", "\t", "\n" })
    @DisplayName("keyword가 null이거나 공백이면 findAllByStatus(ACTIVE)를 호출한다")
    void getSpacesPage_blankKeyword_callsFindAllByStatus(String keyword) {
        // given
        Pageable pageable = PageRequest.of(0, 10);
        List<Space> spaces = List.of(createSpace(1L, "회의실 1"), createSpace(2L, "회의실 2"));
        Page<Space> expectedPage = new PageImpl<>(spaces, pageable, spaces.size());

        given(spaceRepository.findAllByStatus(SpaceStatus.ACTIVE, pageable)).willReturn(expectedPage);

        // when
        Page<Space> actualPage = spaceQueryService.getSpacesPage(pageable, keyword);

        // then
        assertThat(actualPage.getContent()).hasSize(2);
        verify(spaceRepository).findAllByStatus(SpaceStatus.ACTIVE, pageable);
        verify(spaceRepository, never()).searchSpacesByNameOrDescription(any(), any(), any());
    }

    @Test
    @DisplayName("유효한 keyword가 주어지면 searchSpacesByNameOrDescription을 호출한다")
    void getSpacesPage_validKeyword_callsSearch() {
        // given
        String keyword = "회의실";
        Pageable pageable = PageRequest.of(0, 10);
        List<Space> spaces = List.of(createSpace(1L, "회의실 1"));
        Page<Space> expectedPage = new PageImpl<>(spaces, pageable, spaces.size());

        given(spaceRepository.searchSpacesByNameOrDescription(keyword, SpaceStatus.ACTIVE, pageable))
                .willReturn(expectedPage);

        // when
        Page<Space> actualPage = spaceQueryService.getSpacesPage(pageable, keyword);

        // then
        assertThat(actualPage.getContent()).hasSize(1);
        assertThat(actualPage.getContent().get(0).getName()).isEqualTo("회의실 1");
        verify(spaceRepository).searchSpacesByNameOrDescription(keyword, SpaceStatus.ACTIVE, pageable);
        verify(spaceRepository, never()).findAllByStatus(any(), any());
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
