package com.ovengers.slotkey.reservation.policy;

import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationTimePolicyTest {

    private static final LocalTime OPENING = LocalTime.of(9, 0);
    private static final LocalTime CLOSING = LocalTime.of(22, 0);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 17, 10, 0);

    @Test
    @DisplayName("시작 또는 종료 시각이 null이면 VALIDATION_FAILED 예외가 발생한다")
    void validate_nullTime_throwsValidationFailed() {
        LocalDateTime valid = LocalDateTime.of(2026, 9, 17, 14, 0);

        assertThatThrownBy(() -> ReservationTimePolicy.validate(null, valid, OPENING, CLOSING, NOW))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);

        assertThatThrownBy(() -> ReservationTimePolicy.validate(valid, null, OPENING, CLOSING, NOW))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("종료 시각이 시작 시각보다 늦지 않으면 INVALID_RESERVATION_TIME 예외가 발생한다")
    void validate_endNotAfterStart_throwsInvalidTime() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 17, 14, 0);
        LocalDateTime endEqual = LocalDateTime.of(2026, 9, 17, 14, 0);
        LocalDateTime endBefore = LocalDateTime.of(2026, 9, 17, 13, 30);

        assertThatThrownBy(() -> ReservationTimePolicy.validate(start, endEqual, OPENING, CLOSING, NOW))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_RESERVATION_TIME);

        assertThatThrownBy(() -> ReservationTimePolicy.validate(start, endBefore, OPENING, CLOSING, NOW))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_RESERVATION_TIME);
    }

    @Test
    @DisplayName("현재 시각과 같거나 그 이전(과거) 시각으로는 예약할 수 없어 INVALID_RESERVATION_TIME 예외가 발생한다")
    void validate_notFutureStart_throwsInvalidTime() {
        LocalDateTime start = NOW; // now와 같은 시각 -> 미래가 아님
        LocalDateTime end = NOW.plusMinutes(30);

        assertThatThrownBy(() -> ReservationTimePolicy.validate(start, end, OPENING, CLOSING, NOW))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_RESERVATION_TIME);
    }

    @Test
    @DisplayName("30분 단위(분·초·나노초)가 아닌 시각이면 INVALID_RESERVATION_TIME 예외가 발생한다")
    void validate_notSlotAligned_throwsInvalidTime() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 17, 14, 15); // 15분 -> 30분 배수 아님
        LocalDateTime end = LocalDateTime.of(2026, 9, 17, 14, 45);

        assertThatThrownBy(() -> ReservationTimePolicy.validate(start, end, OPENING, CLOSING, NOW))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_RESERVATION_TIME);
    }

    @Test
    @DisplayName("시작과 종료가 서로 다른 날짜면 INVALID_RESERVATION_TIME 예외가 발생한다")
    void validate_differentDate_throwsInvalidTime() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 17, 21, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 18, 21, 30);

        assertThatThrownBy(() -> ReservationTimePolicy.validate(start, end, OPENING, CLOSING, NOW))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_RESERVATION_TIME);
    }

    @Test
    @DisplayName("공간 운영시간을 벗어나면 INVALID_RESERVATION_TIME 예외가 발생한다")
    void validate_outsideOperatingHours_throwsInvalidTime() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 18, 8, 30); // 오픈(9시) 전
        LocalDateTime end = LocalDateTime.of(2026, 9, 18, 9, 0);

        assertThatThrownBy(() -> ReservationTimePolicy.validate(start, end, OPENING, CLOSING, NOW))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_RESERVATION_TIME);
    }

    @Test
    @DisplayName("모든 조건을 만족하는 예약 시간은 예외 없이 통과한다")
    void validate_validTime_doesNotThrow() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 17, 14, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 17, 15, 0);

        assertThatCode(() -> ReservationTimePolicy.validate(start, end, OPENING, CLOSING, NOW))
                .doesNotThrowAnyException();
    }
}
