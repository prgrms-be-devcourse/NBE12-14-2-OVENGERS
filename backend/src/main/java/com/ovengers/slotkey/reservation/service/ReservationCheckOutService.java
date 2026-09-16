package com.ovengers.slotkey.reservation.service;

import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.reservation.dto.response.ReservationResponse;
import com.ovengers.slotkey.reservation.entity.Reservation;
import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import com.ovengers.slotkey.reservation.entity.ReservationStatusHistory;
import com.ovengers.slotkey.reservation.repository.ReservationRepository;
import com.ovengers.slotkey.reservation.repository.ReservationStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 체크아웃(§8-4). 되돌릴 수 없다. 슬롯 반환 없음, 환불 없음 — 예약은 시간 점유권 구매이며
 * 조기 반납을 허용하면 부분 환불 -> 재판매 -> 재입장 연쇄가 생긴다.
 *
 * 자동 퇴실(배치)은 이 서비스를 재사용하지 않는다 — checked_out_at에 배치 실행 시각이 아니라
 * end_time을 넣어야 하기 때문(§3-4). ReservationCompletionScheduler에서 별도로 처리한다.
 */
@Service
@RequiredArgsConstructor
public class ReservationCheckOutService {

    private final ReservationRepository reservationRepository;
    private final ReservationStatusHistoryRepository reservationStatusHistoryRepository;
    private final Clock clock;

    @Transactional
    public ReservationResponse checkOut(Long memberId, Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

        if (!reservation.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_NOT_OWNER);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        int updated = reservationRepository.checkOutIfInUse(
                reservationId, now, ReservationStatus.IN_USE, ReservationStatus.COMPLETED);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.RESERVATION_STATE_CONFLICT, "체크아웃할 수 없는 상태입니다.");
        }

        // TODO(access 도메인 연동 필요, 박창현님): 활성 출입 토큰 revoke.
        // door_access_token.active_reservation_id가 이 reservationId인 활성 토큰을
        // revoked_at=now로 폐기해야 한다(§8-3). access 도메인 완성 후 여기서 어댑터를 호출한다.

        reservationStatusHistoryRepository.save(
                ReservationStatusHistory.of(reservationId, memberId, ReservationStatus.IN_USE, ReservationStatus.COMPLETED, null, now)
        );

        Reservation checkedOut = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        return ReservationResponse.from(checkedOut);
    }
}
