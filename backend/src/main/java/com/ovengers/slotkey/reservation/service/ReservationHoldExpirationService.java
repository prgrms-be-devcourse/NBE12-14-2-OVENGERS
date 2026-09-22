package com.ovengers.slotkey.reservation.service;

import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import com.ovengers.slotkey.reservation.entity.ReservationStatusHistory;
import com.ovengers.slotkey.reservation.repository.ReservationRepository;
import com.ovengers.slotkey.reservation.repository.ReservationSlotRepository;
import com.ovengers.slotkey.reservation.repository.ReservationStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * HELD -> EXPIRED 전이, 슬롯 정리, 상태 이력 저장을 수행하는 단일 만료 서비스.
 * 배치 스케줄러와 신규 예약 슬롯 확보 시 즉시 만료 정리 경로가 공통으로 사용한다(core-domain-decisions 2-3,
 * 3-1).
 */
@Service
@RequiredArgsConstructor
public class ReservationHoldExpirationService {

    private final ReservationRepository reservationRepository;
    private final ReservationSlotRepository reservationSlotRepository;
    private final ReservationStatusHistoryRepository reservationStatusHistoryRepository;

    /**
     * 특정 예약의 HOLD 만료 전이, 슬롯 정리, HOLD_EXPIRED 상태 이력 저장을 수행한다.
     * 호출자의 트랜잭션에 참여하여 원자성을 보장한다.
     *
     * @param reservationId 만료 대상 예약 ID
     * @param now           만료 기준 시각
     * @return 상태 전이와 이력 저장이 성공했으면 true, 이미 만료되었거나 대상이 아니면 false
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean expireHold(Long reservationId, LocalDateTime now) {
        List<Long> ids = List.of(reservationId);
        int updated = reservationRepository.expireHeldReservations(
                ids, now, ReservationStatus.HELD, ReservationStatus.EXPIRED);

        // 조건부 UPDATE가 0건이어도 호출하여 이미 EXPIRED인데 슬롯만 남은 비정상 데이터를 방어적으로 정리한다.
        reservationSlotRepository.deleteSlotsOfExpiredReservations(ids, ReservationStatus.EXPIRED);

        if (updated == 0) {
            return false;
        }

        reservationStatusHistoryRepository.save(
                ReservationStatusHistory.of(reservationId, null, ReservationStatus.HELD, ReservationStatus.EXPIRED,
                        "HOLD_EXPIRED", now));
        return true;
    }
}
