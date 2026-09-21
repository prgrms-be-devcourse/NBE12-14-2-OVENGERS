package com.ovengers.slotkey.access.service;

import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import com.ovengers.slotkey.support.ReservationIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "예약당 활성 출입 토큰은 1개"(CLAUDE.md 규칙 5)를 실제 DB 제약(door_access_token.active_reservation_id UNIQUE)으로 검증한다.
 * 같은 예약에 발급 요청이 동시에 들어와도 활성 토큰은 정확히 1개여야 하고,
 * 제약에 걸려 진 요청은 500이 아니라 409(RESERVATION_STATE_CONFLICT)로 끝나야 한다.
 */
class DoorAccessTokenConcurrencyTest extends ReservationIntegrationTestSupport {

    private static final int THREADS = 8;

    @Autowired
    private DoorAccessTokenService doorAccessTokenService;

    private Long memberId;
    private Long reservationId;

    @BeforeEach
    void setUp() {
        memberId = createMember(0);
        Long spaceId = createSpace();
        reservationId = createReservation(
                memberId, spaceId, ReservationStatus.CONFIRMED, tomorrowAt(14, 0), tomorrowAt(15, 0));
    }

    @Test
    @DisplayName("토큰이 없는 예약에 발급 요청이 동시에 들어와도 활성 토큰은 정확히 1개이고, 진 요청은 409로 끝난다")
    void issue_firstIssueConcurrently_leavesExactlyOneActiveToken() {
        List<Throwable> results = runConcurrently(issueTasks());

        assertThat(successCount(results)).isGreaterThanOrEqualTo(1);
        assertThat(failures(results)).isSubsetOf(ErrorCode.RESERVATION_STATE_CONFLICT);

        assertThat(activeTokenCount()).isEqualTo(1);
        // 성공한 발급 하나당 토큰이 정확히 하나 생긴다(실패한 요청은 롤백되어 흔적이 없다).
        assertThat(totalTokenCount()).isEqualTo(successCount(results));
    }

    @Test
    @DisplayName("이미 활성 토큰이 있는 예약에 재발급 요청이 동시에 들어와도 활성 토큰은 정확히 1개이고, 나머지는 REISSUED로 폐기돼 있다")
    void issue_reissueConcurrently_leavesExactlyOneActiveToken() {
        doorAccessTokenService.issue(memberId, reservationId);

        List<Throwable> results = runConcurrently(issueTasks());

        assertThat(failures(results)).isSubsetOf(ErrorCode.RESERVATION_STATE_CONFLICT);

        assertThat(activeTokenCount()).isEqualTo(1);
        int total = totalTokenCount();
        assertThat(total).isEqualTo(1 + successCount(results));
        assertThat(revokedTokenCount("REISSUED")).isEqualTo(total - 1);
    }

    private List<Runnable> issueTasks() {
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> doorAccessTokenService.issue(memberId, reservationId));
        }
        return tasks;
    }

    private int activeTokenCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM door_access_token WHERE reservation_id = ? AND revoked_at IS NULL",
                Integer.class, reservationId);
    }

    private int totalTokenCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM door_access_token WHERE reservation_id = ?", Integer.class, reservationId);
    }

    private int revokedTokenCount(String reason) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM door_access_token WHERE reservation_id = ? AND revoked_at IS NOT NULL AND revoke_reason = ?",
                Integer.class, reservationId, reason);
    }
}