package com.ovengers.slotkey.reservation.dto.request;

import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 본인 예약 목록 조회 조건.
 * keyword는 공간 이름(Space.name) 부분 일치 검색용(위치/설명 제외).
 * status가 null이면 전체 상태를 조회한다.
 * 페이지 정보(page/size)는 컨트롤러의 Pageable로 받는다(space 도메인과 동일한 방식).
 */
@Schema(description = "본인 예약 목록 검색 조건")
public record ReservationSearchCondition(
        @Schema(description = "예약 상태 필터 (미지정 시 전체)", example = "CONFIRMED")
        ReservationStatus status,
        @Schema(description = "공간 이름 검색어 (부분 일치, 앞뒤 공백 trim 및 공백만 입력 시 전체)", example = "회의실")
        String keyword
) {
}