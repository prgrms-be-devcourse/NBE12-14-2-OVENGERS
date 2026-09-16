package com.ovengers.slotkey.access.policy;

import com.ovengers.slotkey.access.entity.AccessDenyReason;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class DoorAccessTimePolicy {

    // 최초 체크인 허용 시간
    private static final long FIRST_CHECK_IN_MINUTES = 15;

    // 출입 토큰 발급 가능 시간 확인
    public boolean canIssueToken(
            LocalDateTime now,
            LocalDateTime endAt
    ) {
        // 예약 종료 전까지 토큰 발급 가능
        return now.isBefore(endAt);
    }

    // 현재 시각 기준으로 출입 거절 사유 확인
    public Optional<AccessDenyReason> findDenyReason(
            LocalDateTime now,
            LocalDateTime startAt,
            LocalDateTime endAt,
            LocalDateTime checkedInAt
    ) {
        // 예약 종료 시각부터 출입 거절
        if (!now.isBefore(endAt)) {
            return Optional.of(
                    AccessDenyReason.OUTSIDE_ALLOWED_TIME
            );
        }

        // 최초 체크인 여부 확인
        if (checkedInAt == null) {
            return findFirstCheckInDenyReason(
                    now,
                    startAt
            );
        }

        return findReentryDenyReason(
                now,
                checkedInAt
        );
    }

    // 최초 체크인 거절 사유 확인
    private Optional<AccessDenyReason> findFirstCheckInDenyReason(
            LocalDateTime now,
            LocalDateTime startAt
    ) {
        LocalDateTime checkInCloseAt =
                startAt.plusMinutes(FIRST_CHECK_IN_MINUTES);

        // 예약 시작 전이면 출입 거절
        if (now.isBefore(startAt)) {
            return Optional.of(
                    AccessDenyReason.OUTSIDE_ALLOWED_TIME
            );
        }

        // 시작 후 15분이 지나면 출입 거절
        if (now.isAfter(checkInCloseAt)) {
            return Optional.of(
                    AccessDenyReason.OUTSIDE_ALLOWED_TIME
            );
        }

        return Optional.empty();
    }

    // 재입장 거절 사유 확인
    private Optional<AccessDenyReason> findReentryDenyReason(
            LocalDateTime now,
            LocalDateTime checkedInAt
    ) {
        // 최초 체크인 시각과 같거나 이전이면 출입 거절
        if (!now.isAfter(checkedInAt)) {
            return Optional.of(
                    AccessDenyReason.OUTSIDE_ALLOWED_TIME
            );
        }

        return Optional.empty();
    }
}