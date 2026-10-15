package com.ovengers.slotkey.waitlist.service;

import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import com.ovengers.slotkey.support.ReservationIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 구간을 동시에 신청해도 활성 대기가 1건만 남는지 실제 MySQL로 검증한다.
 *
 * 이 경로에는 조건부 UPDATE도, 비관적 락도 없다. 판정자는 `UNIQUE(member_id, space_id,
 * start_time, end_time, active)` 하나이며, UNIQUE의 마지막 칸이 NULL 허용 컬럼이라
 * "MySQL이 UNIQUE에서 NULL을 서로 다른 값으로 본다"는 성질에 기대고 있다. 그래서 글로 적은 보장이
 * 아니라 실측이 필요하다(step-1-review.md M1).
 *
 * 테스트하지 않는 것: 활성 3건 제한의 동시 초과. 제한 검사는 애플리케이션 검사이고
 * docs/plans/waitlist-outbox/01-waitlist-rules.md §1이 "동시 요청 시 초과를 수용한다"로 정했다.
 * 대기 신청은 돈도 슬롯도 움직이지 않으므로 초과 1~2건이 남는 것이 사용자에게 손해가 아니다.
 */
class WaitlistApplyConcurrencyTest extends ReservationIntegrationTestSupport {

    private static final int BALANCE = 100_000;
    private static final int THREADS = 5;

    @Autowired
    private WaitlistService waitlistService;

    private Long memberId;
    private Long spaceId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @BeforeEach
    void setUp() {
        memberId = createMember(BALANCE);
        Long occupierId = createMember(BALANCE);
        spaceId = createSpace();
        startTime = tomorrowAt(14, 0);
        endTime = tomorrowAt(15, 0);
        createReservation(occupierId, spaceId, ReservationStatus.CONFIRMED, startTime, endTime);
    }

    @Test
    @DisplayName("같은 회원이 같은 구간을 동시에 신청하면 한 건만 성공하고 나머지는 WAITLIST_DUPLICATE다")
    void apply_sameRangeConcurrently_keepsSingleActiveEntry() {
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> waitlistService.apply(memberId, spaceId, startTime, endTime));
        }

        List<Throwable> results = runConcurrently(tasks);

        assertThat(successCount(results)).isEqualTo(1);
        assertThat(failures(results))
                .hasSize(THREADS - 1)
                .containsOnly(ErrorCode.WAITLIST_DUPLICATE);
        assertThat(countWaitlistRows("WAITING")).isEqualTo(1);
        assertThat(countActiveWaitlistRows()).isEqualTo(1);
        assertThat(waitlistService.getMyEntries(memberId)).hasSize(1);
    }

    private int countWaitlistRows(String status) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM waitlist_entry WHERE member_id = ? AND status = ?",
                Integer.class, memberId, status);
    }

    private int countActiveWaitlistRows() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM waitlist_entry WHERE member_id = ? AND active IS NOT NULL",
                Integer.class, memberId);
    }
}
