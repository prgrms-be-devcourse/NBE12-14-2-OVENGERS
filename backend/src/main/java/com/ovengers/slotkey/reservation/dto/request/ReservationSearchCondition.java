package com.ovengers.slotkey.reservation.dto.request;

import com.ovengers.slotkey.reservation.entity.ReservationStatus;

/**
 * 본인 예약 목록 조회 조건. status가 null이면 전체 상태를 조회한다.
 * 페이지 정보(page/size)는 컨트롤러의 Pageable로 받는다(space 도메인과 동일한 방식).
 */
public record ReservationSearchCondition(
        ReservationStatus status
) {
}