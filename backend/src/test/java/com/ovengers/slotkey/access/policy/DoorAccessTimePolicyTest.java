package com.ovengers.slotkey.access.policy;

import com.ovengers.slotkey.access.entity.AccessDenyReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class DoorAccessTimePolicyTest {

    private final DoorAccessTimePolicy policy = new DoorAccessTimePolicy();
    private final LocalDateTime startAt = LocalDateTime.of(2026, 9, 16, 14, 0);
    private final LocalDateTime endAt = LocalDateTime.of(2026, 9, 16, 15, 0);

    @Test
    @DisplayName("예약 종료 전에는 출입 토큰을 발급할 수 있다")
    void shouldIssueTokenBeforeReservationEnd() {
        LocalDateTime now = endAt.minusNanos(1);

        boolean result = policy.canIssueToken(
                now,
                endAt
        );

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("예약 종료 시각부터는 출입 토큰을 발급할 수 없다")
    void shouldNotIssueTokenAtReservationEnd() {
        LocalDateTime now = endAt;

        boolean result = policy.canIssueToken(
                now,
                endAt
        );

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("예약 시작 전에는 최초 체크인을 거절한다")
    void shouldRejectFirstCheckInBeforeReservationStart() {
        LocalDateTime now = startAt.minusNanos(1);

        Optional<AccessDenyReason> result = policy.findDenyReason(
                now,
                startAt,
                endAt,
                null
        );

        assertThat(result).contains(AccessDenyReason.OUTSIDE_ALLOWED_TIME);
    }

    @Test
    @DisplayName("최초 체크인은 예약 시작 15분 후까지 가능하다")
    void shouldAllowFirstCheckInUntilFifteenMinutesAfterStart() {
        LocalDateTime now = startAt.plusMinutes(15);

        Optional<AccessDenyReason> result = policy.findDenyReason(
                now,
                startAt,
                endAt,
                null
        );

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("최초 체크인이 예약 시작 15분 후보다 늦으면 거절한다")
    void shouldRejectFirstCheckInAfterFifteenMinutes() {
        LocalDateTime now = startAt.plusMinutes(15).plusNanos(1);

        Optional<AccessDenyReason> result = policy.findDenyReason(
                now,
                startAt,
                endAt,
                null
        );

        assertThat(result).contains(AccessDenyReason.OUTSIDE_ALLOWED_TIME);
    }

    @Test
    @DisplayName("체크인 이후에는 예약 종료 전까지 재입장할 수 있다")
    void shouldAllowReentryBeforeReservationEnd() {
        LocalDateTime checkedInAt = startAt.plusMinutes(5);

        LocalDateTime now = endAt.minusNanos(1);

        Optional<AccessDenyReason> result = policy.findDenyReason(
                now,
                startAt,
                endAt,
                checkedInAt
        );

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("체크인 시각과 같으면 재입장을 거절한다")
    void shouldRejectReentryAtCheckInTime() {
        LocalDateTime checkedInAt = startAt.plusMinutes(5);

        Optional<AccessDenyReason> result = policy.findDenyReason(
                checkedInAt,
                startAt,
                endAt,
                checkedInAt
        );

        assertThat(result).contains(AccessDenyReason.OUTSIDE_ALLOWED_TIME);
    }

    @Test
    @DisplayName("체크인 시각보다 이르면 재입장을 거절한다")
    void shouldRejectReentryBeforeCheckInTime() {
        LocalDateTime checkedInAt = startAt.plusMinutes(5);

        LocalDateTime now = checkedInAt.minusNanos(1);

        Optional<AccessDenyReason> result = policy.findDenyReason(
                now,
                startAt,
                endAt,
                checkedInAt
        );

        assertThat(result).contains(AccessDenyReason.OUTSIDE_ALLOWED_TIME);
    }

    @Test
    @DisplayName("예약 종료 시각부터는 재입장을 거절한다")
    void shouldRejectReentryAtReservationEnd() {
        LocalDateTime checkedInAt = startAt.plusMinutes(5);

        Optional<AccessDenyReason> result = policy.findDenyReason(
                endAt,
                startAt,
                endAt,
                checkedInAt
        );

        assertThat(result).contains(AccessDenyReason.OUTSIDE_ALLOWED_TIME);
    }

}
