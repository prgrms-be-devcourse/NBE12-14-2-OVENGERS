package com.ovengers.slotkey.reservation.service;

import com.ovengers.slotkey.access.service.DoorAccessTokenService;
import com.ovengers.slotkey.credit.service.CreditService;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.reservation.dto.response.ReservationResponse;
import com.ovengers.slotkey.reservation.entity.Reservation;
import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import com.ovengers.slotkey.reservation.entity.ReservationStatusHistory;
import com.ovengers.slotkey.reservation.policy.ReservationRefundPolicy;
import com.ovengers.slotkey.reservation.repository.ReservationRepository;
import com.ovengers.slotkey.reservation.repository.ReservationSlotRepository;
import com.ovengers.slotkey.reservation.repository.ReservationStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 예약 취소(§9, 기획서 "예약 취소" 2026-09-15 갱신). 예약자 본인만 가능하며 시작 이후는
 * 취소할 수 없다(체크아웃으로만 종료). PLATFORM_ADMIN의 강제 취소는 별도 경로
 * (force-cancel, AdminReservationService)로만 처리하며 이 서비스는 다루지 않는다.
 *
 * 취소와 환불은 하나의 트랜잭션에서 함께 커밋된다 — "취소는 됐는데 환불은 실패"하는
 * 중간 상태가 구조적으로 없으므로 별도의 환불 재처리 큐가 필요 없다(ReservationPaymentConfirmService의
 * "차감은 됐는데 확정은 안 된" 중간 상태 없음과 대칭).
 */
@Service
@RequiredArgsConstructor
public class ReservationCancelService {

    private final ReservationRepository reservationRepository;
    private final ReservationSlotRepository reservationSlotRepository;
    private final ReservationStatusHistoryRepository reservationStatusHistoryRepository;
    private final CreditService creditService;
    private final DoorAccessTokenService doorAccessTokenService;
    private final Clock clock;

    @Transactional
    public ReservationResponse cancel(Long memberId, Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

        if (!reservation.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_NOT_OWNER);
        }

        LocalDateTime now = LocalDateTime.now(clock);

        // 취소 가능 여부의 최종 판정(§9). 영향 행이 0이면 이미 취소/완료된 예약이거나
        // 이미 시작된 예약이다 — 조회 시점과 UPDATE 시점 사이의 경합(체크아웃/노쇼 배치와의
        // 동시 처리 포함)도 이 조건부 UPDATE 하나로 막는다.
        int updated = reservationRepository.cancelIfConfirmedAndBeforeStart(
                reservationId, now, ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.RESERVATION_STATE_CONFLICT, "취소할 수 없는 상태입니다.");
        }

        // 점유 슬롯 반환.
        reservationSlotRepository.deleteByReservationId(reservationId);

        // 활성 출입 토큰 revoke. 회원 본인의 취소이므로(이미 위에서 owner 검사를 마쳤음)
        // owner 검사가 없는 revokeByReservation(예약 상태 변경에 따른 시스템 경로)을 사용한다.
        doorAccessTokenService.revokeByReservation(reservationId, now, "CANCELLED");

        // 환불 등급(§9): 시작 1시간 전까지는 전액, 1시간 전~시작 전은 50%만 환급한다.
        // credit_transaction에는 REFUND(+전액)와 PENALTY(-위약금)를 두 줄로 분리해 기록한다
        // (전액 환불이면 위약금이 0이므로 PENALTY 줄은 생략).
        int totalAmount = reservation.getTotalAmount();
        int penaltyAmount = ReservationRefundPolicy.calculatePenaltyAmount(now, reservation.getStartTime(), totalAmount);
        creditService.refund(memberId, reservationId, totalAmount);
        if (penaltyAmount > 0) {
            creditService.penalize(memberId, reservationId, penaltyAmount);
        }

        reservationStatusHistoryRepository.save(
                ReservationStatusHistory.of(reservationId, memberId, ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED, null, now)
        );

        Reservation cancelled = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

        int refundAmount = totalAmount - penaltyAmount;
        return ReservationResponse.ofCancelled(cancelled, refundAmount, penaltyAmount);
    }
}
