package com.ovengers.slotkey.space.entity;

import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.space.dto.request.SpaceUpdateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpaceTest {

        private Space createDefaultSpace() {
                return Space.builder()
                                .id(1L)
                                .name("회의실 A")
                                .location("강남역 1번 출구")
                                .description("조용하고 쾌적한 6인 회의실")
                                .capacity(6)
                                .pricePerSlot(5000L)
                                .imagePath("/images/room-a.jpg")
                                .openingTime(LocalTime.of(9, 0))
                                .closingTime(LocalTime.of(18, 0))
                                .status(SpaceStatus.ACTIVE)
                                .version(0)
                                .build();
        }

        @Test
        @DisplayName("가격 변경 시 pricePerSlot이 갱신되고 version이 1 증가한다")
        void updateDetail_priceChanged_versionIncrements() {
                // given
                Space space = createDefaultSpace();
                SpaceUpdateRequest request = new SpaceUpdateRequest(
                                null, null, null, null, null,
                                6000L, null, null, null, null);

                // when
                space.updateDetail(request);

                // then
                assertThat(space.getPricePerSlot()).isEqualTo(6000L);
                assertThat(space.getVersion()).isEqualTo(1);
        }

        @Test
        @DisplayName("동일한 가격으로 재전송 시 version이 유지된다")
        void updateDetail_samePrice_versionMaintained() {
                // given
                Space space = createDefaultSpace();
                SpaceUpdateRequest request = new SpaceUpdateRequest(
                                null, null, null, null, null,
                                5000L, null, null, null, null);

                // when
                space.updateDetail(request);

                // then
                assertThat(space.getPricePerSlot()).isEqualTo(5000L);
                assertThat(space.getVersion()).isEqualTo(0);
        }

        @Test
        @DisplayName("가격 외의 정보(이름, 위치, 설명, 인원, 이미지, 운영시간, 상태)만 수정 시 version이 유지된다")
        void updateDetail_nonPriceFieldsChanged_versionMaintained() {
                // given
                Space space = createDefaultSpace();
                SpaceUpdateRequest request = new SpaceUpdateRequest(
                                null,
                                "회의실 B",
                                "역삼역 2번 출구",
                                "새로운 설명",
                                8,
                                null,
                                "/images/room-b.jpg",
                                LocalTime.of(10, 0),
                                LocalTime.of(20, 0),
                                SpaceStatus.INACTIVE);

                // when
                space.updateDetail(request);

                // then
                assertThat(space.getName()).isEqualTo("회의실 B");
                assertThat(space.getLocation()).isEqualTo("역삼역 2번 출구");
                assertThat(space.getDescription()).isEqualTo("새로운 설명");
                assertThat(space.getCapacity()).isEqualTo(8);
                assertThat(space.getImagePath()).isEqualTo("/images/room-b.jpg");
                assertThat(space.getOpeningTime()).isEqualTo(LocalTime.of(10, 0));
                assertThat(space.getClosingTime()).isEqualTo(LocalTime.of(20, 0));
                assertThat(space.getStatus()).isEqualTo(SpaceStatus.INACTIVE);
                assertThat(space.getPricePerSlot()).isEqualTo(5000L);
                assertThat(space.getVersion()).isEqualTo(0);
        }

        @Test
        @DisplayName("ABA 가격 변경(5,000 -> 6,000 -> 5,000) 시 최종 가격은 5,000원이고 version은 2가 된다")
        void updateDetail_abaPriceChange_versionIncrementsTwice() {
                // given
                Space space = createDefaultSpace();

                // when 1: 5,000 -> 6,000
                space.updateDetail(new SpaceUpdateRequest(
                                null, null, null, null, null,
                                6000L, null, null, null, null));
                assertThat(space.getPricePerSlot()).isEqualTo(6000L);
                assertThat(space.getVersion()).isEqualTo(1);

                // when 2: 6,000 -> 5,000
                space.updateDetail(new SpaceUpdateRequest(
                                null, null, null, null, null,
                                5000L, null, null, null, null));

                // then
                assertThat(space.getPricePerSlot()).isEqualTo(5000L);
                assertThat(space.getVersion()).isEqualTo(2);
        }

        @Test
        @DisplayName("openingTime만 수정 시 기존 closingTime보다 이르면 운영시간이 갱신된다")
        void updateDetail_openingTimeOnly_valid_success() {
                // given: 09:00 ~ 18:00
                Space space = createDefaultSpace();
                SpaceUpdateRequest request = new SpaceUpdateRequest(
                                null, null, null, null, null,
                                null, null, LocalTime.of(8, 0), null, null);

                // when
                space.updateDetail(request);

                // then
                assertThat(space.getOpeningTime()).isEqualTo(LocalTime.of(8, 0));
                assertThat(space.getClosingTime()).isEqualTo(LocalTime.of(18, 0));
        }

        @Test
        @DisplayName("openingTime만 수정 시 기존 closingTime과 같거나 늦으면 INVALID_OPERATING_HOURS 예외가 발생하고 운영시간이 유지된다")
        void updateDetail_openingTimeOnly_invalid_throwsException() {
                // given: 09:00 ~ 18:00
                Space space = createDefaultSpace();
                SpaceUpdateRequest sameTimeRequest = new SpaceUpdateRequest(
                                null, null, null, null, null,
                                null, null, LocalTime.of(18, 0), null, null);

                // when & then (같을 때)
                assertThatThrownBy(() -> space.updateDetail(sameTimeRequest))
                                .isInstanceOf(BusinessException.class)
                                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_OPERATING_HOURS);
                assertThat(space.getOpeningTime()).isEqualTo(LocalTime.of(9, 0));

                // when & then (더 늦을 때)
                SpaceUpdateRequest laterRequest = new SpaceUpdateRequest(
                                null, null, null, null, null,
                                null, null, LocalTime.of(19, 0), null, null);
                assertThatThrownBy(() -> space.updateDetail(laterRequest))
                                .isInstanceOf(BusinessException.class)
                                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_OPERATING_HOURS);
                assertThat(space.getOpeningTime()).isEqualTo(LocalTime.of(9, 0));
        }

        @Test
        @DisplayName("closingTime만 수정 시 기존 openingTime보다 늦으면 운영시간이 갱신된다")
        void updateDetail_closingTimeOnly_valid_success() {
                // given: 09:00 ~ 18:00
                Space space = createDefaultSpace();
                SpaceUpdateRequest request = new SpaceUpdateRequest(
                                null, null, null, null, null,
                                null, null, null, LocalTime.of(21, 0), null);

                // when
                space.updateDetail(request);

                // then
                assertThat(space.getOpeningTime()).isEqualTo(LocalTime.of(9, 0));
                assertThat(space.getClosingTime()).isEqualTo(LocalTime.of(21, 0));
        }

        @Test
        @DisplayName("closingTime만 수정 시 기존 openingTime과 같거나 이르면 INVALID_OPERATING_HOURS 예외가 발생하고 운영시간이 유지된다")
        void updateDetail_closingTimeOnly_invalid_throwsException() {
                // given: 09:00 ~ 18:00
                Space space = createDefaultSpace();
                SpaceUpdateRequest sameTimeRequest = new SpaceUpdateRequest(
                                null, null, null, null, null,
                                null, null, null, LocalTime.of(9, 0), null);

                // when & then (같을 때)
                assertThatThrownBy(() -> space.updateDetail(sameTimeRequest))
                                .isInstanceOf(BusinessException.class)
                                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_OPERATING_HOURS);
                assertThat(space.getClosingTime()).isEqualTo(LocalTime.of(18, 0));

                // when & then (더 이를 때)
                SpaceUpdateRequest earlierRequest = new SpaceUpdateRequest(
                                null, null, null, null, null,
                                null, null, null, LocalTime.of(8, 0), null);
                assertThatThrownBy(() -> space.updateDetail(earlierRequest))
                                .isInstanceOf(BusinessException.class)
                                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_OPERATING_HOURS);
                assertThat(space.getClosingTime()).isEqualTo(LocalTime.of(18, 0));
        }

        @ParameterizedTest
        @ValueSource(longs = { 0L, -100L, -5000L, 5050L, 1050L, 99L })
        @DisplayName("가격 수정 시 0, 음수 또는 100원 단위가 아닌 값이면 INVALID_PRICE_UNIT 예외가 발생한다")
        void updateDetail_invalidPrice_throwsException(Long invalidPrice) {
                // given
                Space space = createDefaultSpace();
                SpaceUpdateRequest request = new SpaceUpdateRequest(
                                null, null, null, null, null,
                                invalidPrice, null, null, null, null);

                // when & then
                assertThatThrownBy(() -> space.updateDetail(request))
                                .isInstanceOf(BusinessException.class)
                                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PRICE_UNIT);
                assertThat(space.getPricePerSlot()).isEqualTo(5000L);
                assertThat(space.getVersion()).isEqualTo(0);
        }

        @Test
        @DisplayName("updateStatus 호출 시 공간의 상태가 변경된다")
        void updateStatus_changesStatus() {
                // given
                Space space = createDefaultSpace();
                assertThat(space.getStatus()).isEqualTo(SpaceStatus.ACTIVE);

                // when
                space.updateStatus(SpaceStatus.INACTIVE);

                // then
                assertThat(space.getStatus()).isEqualTo(SpaceStatus.INACTIVE);
        }

        @Test
        @DisplayName("validateOperatingHours는 null, 시간 순서 오류 또는 30분 경계가 아닌 시각에 예외를 발생시킨다")
        void validateOperatingHours_throwsException() {
                assertThatThrownBy(() -> Space.validateOperatingHours(null, LocalTime.of(18, 0)))
                                .isInstanceOf(BusinessException.class)
                                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_OPERATING_HOURS);

                assertThatThrownBy(() -> Space.validateOperatingHours(LocalTime.of(9, 0), null))
                                .isInstanceOf(BusinessException.class)
                                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_OPERATING_HOURS);

                assertThatThrownBy(() -> Space.validateOperatingHours(LocalTime.of(18, 0), LocalTime.of(9, 0)))
                                .isInstanceOf(BusinessException.class)
                                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_OPERATING_HOURS);

                assertThatThrownBy(() -> Space.validateOperatingHours(LocalTime.of(9, 0), LocalTime.of(9, 0)))
                                .isInstanceOf(BusinessException.class)
                                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_OPERATING_HOURS);

                assertThatThrownBy(() -> Space.validateOperatingHours(LocalTime.of(9, 15), LocalTime.of(18, 0)))
                                .isInstanceOf(BusinessException.class)
                                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_OPERATING_HOURS);

                assertThatThrownBy(() -> Space.validateOperatingHours(LocalTime.of(9, 0), LocalTime.of(18, 30, 1)))
                                .isInstanceOf(BusinessException.class)
                                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_OPERATING_HOURS);
        }
}
