package com.ovengers.slotkey.stats.dto.response;

import com.ovengers.slotkey.space.entity.SpaceStatus;

public record SpaceRankingItem(
        int rank,
        Integer previousRank,
        Long spaceId,
        String spaceName,
        String location,
        SpaceStatus spaceStatus,
        long netRevenue,
        int reservationCount,
        int slotCount
) {
}
