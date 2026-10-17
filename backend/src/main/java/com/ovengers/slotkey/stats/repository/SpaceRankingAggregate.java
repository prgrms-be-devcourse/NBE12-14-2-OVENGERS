package com.ovengers.slotkey.stats.repository;

/** 한 기간의 공간별 집계 결과(저장 전). */
public record SpaceRankingAggregate(
        Long spaceId,
        String location,
        long netRevenue,
        int reservationCount,
        int slotCount
) {
    public boolean isAllZero() {
        return netRevenue == 0 && reservationCount == 0 && slotCount == 0;
    }
}
