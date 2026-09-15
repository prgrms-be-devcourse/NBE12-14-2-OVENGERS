package com.ovengers.slotkey.space.service;

import com.ovengers.slotkey.audit.entity.AuditAction;
import com.ovengers.slotkey.audit.entity.AuditTargetType;
import com.ovengers.slotkey.audit.service.AuditLogService;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.space.dto.request.SpaceCreateRequest;
import com.ovengers.slotkey.space.dto.request.SpaceUpdateRequest;
import com.ovengers.slotkey.space.dto.response.SpaceDetailResponse;
import com.ovengers.slotkey.space.entity.Space;
import com.ovengers.slotkey.space.entity.SpaceStatus;
import com.ovengers.slotkey.space.repository.SpaceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminSpaceServiceTest {

        @Mock
        private SpaceRepository spaceRepository;

        @Mock
        private AuditLogService auditLogService;

        @InjectMocks
        private AdminSpaceService adminSpaceService;

        @Test
        @DisplayName("공간 등록에 성공하면 저장된 공간 정보를 반환하고 REGISTER_SPACE 감사 로그를 기록한다")
        void createSpace_success() {
                // given
                Long adminMemberId = 100L;
                SpaceCreateRequest request = new SpaceCreateRequest(
                                "컨퍼런스 룸",
                                "서울시 테헤란로 123",
                                "최대 10인 회의실",
                                10,
                                5000L,
                                "/images/conf.jpg",
                                LocalTime.of(9, 0),
                                LocalTime.of(22, 0));

                Space savedSpace = Space.builder()
                                .id(1L)
                                .name(request.name())
                                .location(request.location())
                                .description(request.description())
                                .capacity(request.capacity())
                                .pricePerSlot(request.pricePerSlot())
                                .imagePath(request.imagePath())
                                .openingTime(request.openingTime())
                                .closingTime(request.closingTime())
                                .status(SpaceStatus.ACTIVE)
                                .build();

                given(spaceRepository.save(any(Space.class))).willReturn(savedSpace);

                // when
                SpaceDetailResponse response = adminSpaceService.createSpace(request, adminMemberId);

                // then
                assertThat(response.id()).isEqualTo(1L);
                assertThat(response.name()).isEqualTo("컨퍼런스 룸");
                assertThat(response.pricePerSlot()).isEqualTo(5000L);
                assertThat(response.status()).isEqualTo(SpaceStatus.ACTIVE);

                // 감사 로그 호출 검증
                verify(auditLogService).log(
                                eq(adminMemberId),
                                eq(AuditAction.REGISTER_SPACE),
                                eq(AuditTargetType.SPACE),
                                eq(1L),
                                any(),
                                any(SpaceDetailResponse.class));
        }

        @Test
        @DisplayName("100원 단위가 아닌 요금으로 공간 등록 시 INVALID_PRICE_UNIT 예외가 발생한다")
        void createSpace_invalidPriceUnit_throwsException() {
                // given
                SpaceCreateRequest request = new SpaceCreateRequest(
                                "회의실",
                                "강남",
                                "설명",
                                4,
                                5250L, // 100원 단위 위반
                                null,
                                LocalTime.of(9, 0),
                                LocalTime.of(18, 0));

                // when & then
                assertThatThrownBy(() -> adminSpaceService.createSpace(request, 1L))
                                .isInstanceOf(BusinessException.class)
                                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PRICE_UNIT);
        }

        @Test
        @DisplayName("운영 시작 시각이 종료 시각과 같거나 늦으면 INVALID_OPERATING_HOURS 예외가 발생한다")
        void createSpace_invalidOperatingHours_throwsException() {
                // given
                SpaceCreateRequest request = new SpaceCreateRequest(
                                "회의실",
                                "강남",
                                "설명",
                                4,
                                5000L,
                                null,
                                LocalTime.of(18, 0), // 시작이 종료보다 늦음
                                LocalTime.of(9, 0));

                // when & then
                assertThatThrownBy(() -> adminSpaceService.createSpace(request, 1L))
                                .isInstanceOf(BusinessException.class)
                                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_OPERATING_HOURS);
        }

        @Test
        @DisplayName("공간 수정에 성공하면 변경사항을 반영하고 MODIFY_SPACE 감사 로그를 기록한다")
        void updateSpace_success() {
                // given
                Long spaceId = 1L;
                Long adminMemberId = 100L;

                Space existingSpace = Space.builder()
                                .id(spaceId)
                                .name("이전 공간명")
                                .location("이전 위치")
                                .description("이전 설명")
                                .capacity(4)
                                .pricePerSlot(3000L)
                                .openingTime(LocalTime.of(9, 0))
                                .closingTime(LocalTime.of(18, 0))
                                .status(SpaceStatus.ACTIVE)
                                .build();

                SpaceUpdateRequest updateRequest = new SpaceUpdateRequest(
                                null,
                                "새 공간명",
                                "새 위치",
                                "새 설명",
                                8,
                                4000L,
                                "/new-image.jpg",
                                LocalTime.of(10, 0),
                                LocalTime.of(20, 0),
                                SpaceStatus.INACTIVE);

                given(spaceRepository.findById(spaceId)).willReturn(Optional.of(existingSpace));

                // when
                SpaceDetailResponse response = adminSpaceService.updateSpace(spaceId, updateRequest, adminMemberId);

                // then
                assertThat(response.name()).isEqualTo("새 공간명");
                assertThat(response.capacity()).isEqualTo(8);
                assertThat(response.pricePerSlot()).isEqualTo(4000L);
                assertThat(response.status()).isEqualTo(SpaceStatus.INACTIVE);

                // 감사 로그 호출 검증
                verify(auditLogService).log(
                                eq(adminMemberId),
                                eq(AuditAction.MODIFY_SPACE),
                                eq(AuditTargetType.SPACE),
                                eq(spaceId),
                                any(SpaceDetailResponse.class),
                                any(SpaceDetailResponse.class));
        }

        @Test
        @DisplayName("존재하지 않는 spaceId 수정 시 SPACE_NOT_FOUND 예외가 발생한다")
        void updateSpace_notFound_throwsException() {
                // given
                Long invalidId = 999L;
                SpaceUpdateRequest updateRequest = new SpaceUpdateRequest(
                                null, "이름", "위치", "설명", 4, 3000L, null, null, null, null);

                given(spaceRepository.findById(invalidId)).willReturn(Optional.empty());

                // when & then
                assertThatThrownBy(() -> adminSpaceService.updateSpace(invalidId, updateRequest, 1L))
                                .isInstanceOf(BusinessException.class)
                                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SPACE_NOT_FOUND);
        }
}
