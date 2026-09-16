package com.ovengers.slotkey.reservation.scheduler;

import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import com.ovengers.slotkey.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Predicate;

/**
 * 시간 경과로 일어나는 예약 상태 전이 3종(core-domain-decisions 3-4).
 * - HELD -> EXPIRED (홀드 만료)
 * - CONFIRMED -> NO_SHOW (start + 15분까지 미체크인)
 * - IN_USE -> COMPLETED (종료 시각 경과, 자동 퇴실)
 *
 * "배치는 청소부지 심판이 아니다"(core-domain-decisions 2-3): 슬롯 정합성은 예약 생성 시점 정리가 보장하고,
 * 이 배치는 늦게 돌아도 틀린 결과를 만들지 않도록 건별 조건부 UPDATE로만 전이한다.
 * 한 건이 실패해도 나머지는 계속 처리한다(건별 트랜잭션, ReservationBatchProcessor).
 *
 * 각 메서드는 public이라 테스트에서 스케줄 없이 직접 호출할 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationCompletionScheduler {

    private final ReservationRepository reservationRepository;
    private final ReservationBatchProcessor processor;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${slotkey.scheduler.reservation.fixed-delay-ms:60000}")
    public void expireHolds() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> ids = reservationRepository.findExpiredHoldIds(now, ReservationStatus.HELD);
        processEach("HOLD_EXPIRE", ids, id -> processor.expireHold(id, now));
    }

    @Scheduled(fixedDelayString = "${slotkey.scheduler.reservation.fixed-delay-ms:60000}")
    public void markNoShows() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> ids = reservationRepository.findNoShowCandidateIds(
                now.minus(ReservationBatchProcessor.CHECK_IN_WINDOW), ReservationStatus.CONFIRMED);
        processEach("NO_SHOW", ids, id -> processor.markNoShow(id, now));
    }

    @Scheduled(fixedDelayString = "${slotkey.scheduler.reservation.fixed-delay-ms:60000}")
    public void autoCheckOut() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> ids = reservationRepository.findAutoCheckOutCandidateIds(now, ReservationStatus.IN_USE);
        processEach("AUTO_CHECK_OUT", ids, id -> processor.autoCheckOut(id, now));
    }

    private void processEach(String jobName, List<Long> reservationIds, Predicate<Long> transition) {
        if (reservationIds.isEmpty()) {
            return;
        }
        int transitioned = 0;
        for (Long reservationId : reservationIds) {
            try {
                if (transition.test(reservationId)) {
                    transitioned++;
                }
            } catch (RuntimeException e) {
                log.error("[{}] reservationId={} 처리 실패", jobName, reservationId, e);
            }
        }
        log.info("[{}] 대상 {}건 중 {}건 전이", jobName, reservationIds.size(), transitioned);
    }
}