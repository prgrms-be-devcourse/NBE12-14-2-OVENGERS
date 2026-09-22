package com.ovengers.slotkey.reservation.service;

import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.reservation.entity.ReservationSlot;
import com.ovengers.slotkey.reservation.repository.ReservationSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 슬롯 점유의 유일한 진실(core-domain-decisions 4-1, core-domain-decisions 4-2). 동시 예약 차단은 UNIQUE(space_id, slot_start)가
 * 담당하고, 이 서비스는 그 제약 위반을 도메인 예외 하나로 모으는 역할만 한다.
 */
@Service
@RequiredArgsConstructor
public class ReservationSlotService {

    private static final int SLOT_MINUTES = 30;

    private final ReservationSlotRepository reservationSlotRepository;
    private final ReservationHoldExpirationService reservationHoldExpirationService;
    private final Clock clock;

    /** [start, end) 구간을 30분 단위 슬롯 시작 시각 목록으로 쪼갠다. */
    public List<LocalDateTime> buildSlotStarts(LocalDateTime start, LocalDateTime end) {
        List<LocalDateTime> starts = new java.util.ArrayList<>();
        for (LocalDateTime cursor = start; cursor.isBefore(end); cursor = cursor.plusMinutes(SLOT_MINUTES)) {
            starts.add(cursor);
        }
        return starts;
    }

    /**
     * 해당 슬롯들을 reservationId 앞으로 확보한다. 만료된 HELD가 있다면 먼저 정리하고
     * (core-domain-decisions 2-3), 그래도 살아있는 점유가 있으면(UNIQUE 위반) 409로 변환한다(core-domain-decisions 4-2).
     * 사전 가용성 조회는 하지 않는다 — INSERT의 성패가 유일한 판정이다.
     */
    @Transactional
    public void secureSlots(Long reservationId, Long spaceId, List<LocalDateTime> slotStarts) {
        cleanupExpiredHolds(spaceId, slotStarts);

        List<ReservationSlot> slots = slotStarts.stream()
                .map(start -> ReservationSlot.of(reservationId, spaceId, start))
                .toList();
        try {
            reservationSlotRepository.saveAll(slots);
            reservationSlotRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.RESERVATION_SLOT_CONFLICT);
        }
    }

    private void cleanupExpiredHolds(Long spaceId, List<LocalDateTime> slotStarts) {
        List<Long> candidateReservationIds =
                reservationSlotRepository.findReservationIdsBySpaceIdAndSlotStartIn(spaceId, slotStarts);
        if (candidateReservationIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        for (Long candidateId : candidateReservationIds) {
            reservationHoldExpirationService.expireHold(candidateId, now);
        }
    }
}
