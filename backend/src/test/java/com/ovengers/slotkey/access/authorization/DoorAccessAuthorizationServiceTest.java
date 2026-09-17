package com.ovengers.slotkey.access.authorization;

import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

public class DoorAccessAuthorizationServiceTest {
    private final DoorAccessAuthorizationService service = new DoorAccessAuthorizationService();

    @Test
    @DisplayName("로그인 회원과 예약 회원이 같으면 예약 소유자이다")
    void shouldReturnTrueWhenMemberIsReservationOwner() {
        Long loginMemberId = 1L;
        Long reservationMemberId = 1L;

        boolean result = service.isOwner(
                loginMemberId,
                reservationMemberId
        );

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("로그인 회원과 예약 회원이 다르면 예약 소유자가 아니다")
    void shouldReturnFalseWhenMemberIsNotReservationOwner() {
        Long loginMemberId = 1L;
        Long reservationMemberId = 2L;

        boolean result = service.isOwner(
                loginMemberId,
                reservationMemberId
        );

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("예약 소유자가 일치하면 예외가 발생하지 않는다")
    void shouldNotThrowWhenMemberIsReservationOwner() {
        Long loginMemberId = 1L;
        Long reservationMemberId = 1L;

        assertThatCode(() -> service.validateOwner(
                loginMemberId,
                reservationMemberId
        )).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("예약 소유자가 다르면 접근 권한 예외가 발생한다")
    void shouldThrowWhenMemberIsNotReservationOwner() {
        Long loginMemberId = 1L;
        Long reservationMemberId = 2L;

        assertThatThrownBy(() -> service.validateOwner(
                loginMemberId,
                reservationMemberId
        )).isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN_NOT_OWNER));
    }
}
