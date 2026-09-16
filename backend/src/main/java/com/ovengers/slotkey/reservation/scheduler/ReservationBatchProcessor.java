package com.ovengers.slotkey.reservation.scheduler;

import com.ovengers.slotkey.reservation.entity.Reservation;
import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import com.ovengers.slotkey.reservation.entity.ReservationStatusHistory;
import com.ovengers.slotkey.reservation.repository.ReservationRepository;
import com.ovengers.slotkey.reservation.repository.ReservationSlotRepository;
import com.ovengers.slotkey.reservation.repository.ReservationStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 배치 전이를 예약 1건 = 트랜잭션 1개로 처리한다.
 * 스케줄러와 다른 빈으로 분리한 이유: 같은 클래스 안에서 @Transactional 메서드를 호출하면
 * 프록시를 거치지 않아 트랜잭션이 걸리지 않는다(self-invocation).
 *
 * 모든 메서드는 "조건부 UPDATE 영향 행이 1일 때만 후속 처리 + 이력 저장"(§3-1) 패턴을 따르며,
 * 실제로 전이했으면 true, 사용자 요청 등에 밀려 전이하지 않았으면 false를 돌려준다.
 * 시스템 작업이므로 이력의 changedByMemberId는 null이다.
 */
@Component
@RequiredArgsConstructor
public class ReservationBatchProcessor {

    /** 최초 체크인 허용 구간 = NO_SHOW 판정 기준(§6-3, §8-1). 두 개의 규칙이 아니라 하나의 값이다. */
    static final Duration CHECK_IN_WINDOW = Duration.ofMinutes(15);

    private final ReservationRepository reservationRepository;
    private final ReservationSlotRepository reservationSlotRepository;
    private final ReservationStatusHistoryRepository reservationStatusHistoryRepository;

    /** HELD -> EXPIRED + 슬롯 삭제(§2-3). 예약 생성 시점 정리와 같은 쿼리를 재사용한다. */
    @Transactional
    public boolean expireHold(Long reservationId, LocalDateTime now) {
        List<Long> ids = List.of(reservationId);
        int updated = reservationRepository.expireHeldReservations(
                ids, now, ReservationStatus.HELD, ReservationStatus.EXPIRED);
        if (updated == 0) {
            return false;
        }
        reservationSlotRepository.deleteSlotsOfExpiredReservations(ids, ReservationStatus.EXPIRED);
        saveHistory(reservationId, ReservationStatus.HELD, ReservationStatus.EXPIRED, "HOLD_EXPIRED", now);
        return true;
    }

    /** CONFIRMED -> NO_SHOW + 슬롯 전부 삭제, 환불 없음(§6-3). */
    @Transactional
    public boolean markNoShow(Long reservationId, LocalDateTime now) {
        int updated = reservationRepository.markNoShowIfNotCheckedIn(
                reservationId, now.minus(CHECK_IN_WINDOW), ReservationStatus.CONFIRMED, ReservationStatus.NO_SHOW);
        if (updated == 0) {
            return false;
        }
        reservationSlotRepository.deleteByReservationId(reservationId);

        // TODO(access 도메인 연동 필요, 박창현님): 활성 출입 토큰 revoke(§3-2 NO_SHOW -> 토큰 폐기).

        saveHistory(reservationId, ReservationStatus.CONFIRMED, ReservationStatus.NO_SHOW, "NO_SHOW", now);
        return true;
    }

    /**
     * IN_USE -> COMPLETED(자동 퇴실, §3-4). 슬롯 반환 없음, 환불 없음(§8-4).
     * checked_out_at과 이력의 changedAt 모두 배치 실행 시각이 아니라 end_time을 쓴다.
     */
    @Transactional
    public boolean autoCheckOut(Long reservationId, LocalDateTime now) {
        int updated = reservationRepository.autoCheckOutIfEnded(
                reservationId, now, ReservationStatus.IN_USE, ReservationStatus.COMPLETED);
        if (updated == 0) {
            return false;
        }
        // clearAutomatically = true 이므로 UPDATE 이후의 값(end_time)을 다시 읽는다.
        Reservation completed = reservationRepository.findById(reservationId).orElseThrow();

        // TODO(access 도메인 연동 필요, 박창현님): 활성 출입 토큰 revoke(§3-2 COMPLETED -> 토큰 폐기).

        saveHistory(reservationId, ReservationStatus.IN_USE, ReservationStatus.COMPLETED,
                "AUTO_CHECK_OUT", completed.getEndTime());
        return true;
    }

    private void saveHistory(Long reservationId, ReservationStatus from, ReservationStatus to,
                             String reason, LocalDateTime changedAt) {
        reservationStatusHistoryRepository.save(
                ReservationStatusHistory.of(reservationId, null, from, to, reason, changedAt));
    }
}