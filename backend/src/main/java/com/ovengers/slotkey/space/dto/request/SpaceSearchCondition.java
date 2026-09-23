package com.ovengers.slotkey.space.dto.request;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 공간 목록 조회 조건. 모든 필드는 선택이며 null이면 해당 조건은 적용하지 않는다.
 * date/startTime/endTime을 모두 지정하면 그 시간대에 점유된 슬롯이 하나도 없는 공간만 반환한다
 * (참고용 스냅샷 — 실제 예약 가능 여부는 예약 생성 시점에 다시 검증된다, api-spec 4-3 원칙과 동일).
 */
public record SpaceSearchCondition(
        String keyword,
        String location,
        Long minPrice,
        Long maxPrice,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime
) {
    public boolean hasTimeFilter() {
        return date != null && startTime != null && endTime != null;
    }
}
