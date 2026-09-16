package com.ovengers.slotkey.reservation.service;

import com.ovengers.slotkey.credit.service.CreditService;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.reservation.dto.response.ReservationResponse;
import com.ovengers.slotkey.reservation.entity.Reservation;
import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import com.ovengers.slotkey.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 연장(§7). 기존 슬롯은 건드리지 않고 뒤에 슬롯을 더 붙이는 방식이라, 실패해도
 * 원 예약은 무손상이다. 남의 점유가 HELD인지 CONFIRMED인지 구분하지 않는다 — 슬롯 행이
 * 존재하면 그냥 점유다(core-domain-decisions 7-1). 유일한 예외인 만료된 HELD 정리는 secureSlots가 처리한다.
 */
@Service
@RequiredArgsConstructor
public class ReservationExtendService {

    private final ReservationRepository reservationRepository;
    private final ReservationSlotService reservationSlotService;
    private final PricingService pricingService;
    private final CreditService creditService;
    private final Clock clock;

    @Transactional
    public ReservationResponse extend(Long memberId, Long reservationId, LocalDateTime expectedEndTime, LocalDateTime newEndTime) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

        if (!reservation.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_NOT_OWNER);
        }
        if (reservation.getStatus() != ReservationStatus.CONFIRMED && reservation.getStatus() != ReservationStatus.IN_USE) {
            throw new BusinessException(ErrorCode.RESERVATION_EXTEND_NOT_ALLOWED, "연장할 수 없는 상태입니다.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (!now.isBefore(reservation.getEndTime())) {
            throw new BusinessException(ErrorCode.RESERVATION_EXTEND_NOT_ALLOWED, "이미 종료된 예약은 연장할 수 없습니다.");
        }
        if (!reservation.getEndTime().isEqual(expectedEndTime)) {
            throw new BusinessException(ErrorCode.RESERVATION_STATE_CONFLICT, "예약 정보가 변경되었습니다. 다시 시도해주세요.");
        }
        if (!newEndTime.isAfter(reservation.getEndTime())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "연장 시각은 기존 종료 시각보다 늦어야 합니다.");
        }

        // 추가 슬롯 확보. 실패(RESERVATION_SLOT_CONFLICT) 시 전파되어 아래 크레딧 차감/UPDATE는
        // 시도조차 되지 않고, 이미 삽입 시도한 슬롯도 트랜잭션과 함께 롤백된다.
        List<LocalDateTime> additionalSlotStarts =
                reservationSlotService.buildSlotStarts(reservation.getEndTime(), newEndTime);
        reservationSlotService.secureSlots(reservationId, reservation.getSpaceId(), additionalSlotStarts);

        // 금액은 반드시 원 예약의 price_per_slot_snapshot 기준이다 — 같은 이용 건 후반부만
        // 비싸지면 사용자가 납득하지 못한다(core-domain-decisions 7-2).
        int additionalAmount = pricingService.calculateTotalAmount(
                reservation.getPricePerSlotSnapshot(), additionalSlotStarts.size());
        int newTotalAmount = reservation.getTotalAmount() + additionalAmount;

        creditService.charge(memberId, reservationId, additionalAmount);

        int updated = reservationRepository.extendIfEndTimeMatches(
                reservationId, expectedEndTime, newEndTime, newTotalAmount,
                ReservationStatus.CONFIRMED, ReservationStatus.IN_USE);
        if (updated == 0) {
            // 연장↔연장 중복 제출 또는 연장↔취소/체크아웃 경합. 슬롯 삽입과 크레딧 차감을
            // 포함한 트랜잭션 전체가 롤백된다.
            throw new BusinessException(ErrorCode.RESERVATION_STATE_CONFLICT, "연장에 실패했습니다. 다시 시도해주세요.");
        }

        Reservation extended = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        return ReservationResponse.from(extended);
    }
}
