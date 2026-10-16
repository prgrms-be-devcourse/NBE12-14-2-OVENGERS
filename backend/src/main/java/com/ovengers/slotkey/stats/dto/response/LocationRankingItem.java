package com.ovengers.slotkey.stats.dto.response;

public record LocationRankingItem(
        int rank,
        Integer previousRank,
        String location,
        int spaceCount,
        long netRevenue,
        int reservationCount,
        int slotCount
) {
}
