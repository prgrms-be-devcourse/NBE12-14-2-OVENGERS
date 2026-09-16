package com.ovengers.slotkey.reservation.service;

import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.reservation.dto.response.ReservationResponse;
import com.ovengers.slotkey.reservation.entity.Reservation;
import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import com.ovengers.slotkey.reservation.entity.ReservationStatusHistory;
import com.ovengers.slotkey.reservation.policy.ReservationTimePolicy;
import com.ovengers.slotkey.reservation.repository.ReservationRepository;
import com.ovengers.slotkey.reservation.repository.ReservationStatusHistoryRepository;
import com.ovengers.slotkey.space.entity.Space;
import com.ovengers.slotkey.space.entity.SpaceStatus;
import com.ovengers.slotkey.space.repository.SpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 예약 1단계: HOLD 생성(core-domain-decisions 2-1). 회원/공간 검증 -> 가격계산 -> 슬롯확보 -> HOLD 저장까지
 * 하나의 트랜잭션이며, 결제는 이 단계에 없다(구 ReservationCreateService는 1단계 설계
 * 흔적이라 2단계 확정 이후 역할을 HOLD 생성으로 좁히며 이 이름으로 정리했다).
 *
 * 회원 활성 여부는 이 서비스가 다시 확인하지 않는다 — CustomAuthenticationFilter가 요청
 * 시점에 이미 DB에서 회원 상태를 재조회해 ACTIVE가 아니면 요청을 차단하므로, 여기 도달한
 * 시점의 memberId는 이미 활성 회원임이 보장된다.
 */
@Service
@RequiredArgsConstructor
public class ReservationHoldService {

    private static final int HOLD_MINUTES = 10;

    private final SpaceRepository spaceRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationStatusHistoryRepository reservationStatusHistoryRepository;
    private final PricingService pricingService;
    private final ReservationSlotService reservationSlotService;
    private final Clock clock;

    @Transactional
    public ReservationResponse createHold(Long memberId, Long spaceId, LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime now = LocalDateTime.now(clock);

        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SPACE_NOT_FOUND));
        if (space.getStatus() != SpaceStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.SPACE_INACTIVE);
        }

        ReservationTimePolicy.validate(startTime, endTime, space.getOpeningTime(), space.getClosingTime(), now);

        int slotCount = pricingService.calculateSlotCount(startTime, endTime);
        int pricePerSlotSnapshot = Math.toIntExact(space.getPricePerSlot());
        int totalAmount = pricingService.calculateTotalAmount(pricePerSlotSnapshot, slotCount);

        Reservation reservation = Reservation.createHeld(
                memberId, spaceId, startTime, endTime,
                pricePerSlotSnapshot, totalAmount,
                now, now.plusMinutes(HOLD_MINUTES)
        );
        reservation = reservationRepository.save(reservation);

        List<LocalDateTime> slotStarts = reservationSlotService.buildSlotStarts(startTime, endTime);
        // 슬롯 확보 실패(RESERVATION_SLOT_CONFLICT) 시 예외가 전파되어 위의 예약 INSERT를
        // 포함한 트랜잭션 전체가 롤백된다 — HELD 잔재가 남지 않는다.
        reservationSlotService.secureSlots(reservation.getId(), spaceId, slotStarts);

        reservationStatusHistoryRepository.save(
                ReservationStatusHistory.of(reservation.getId(), memberId, null, ReservationStatus.HELD, null, now)
        );

        return ReservationResponse.from(reservation, space.getVersion());
    }
}
