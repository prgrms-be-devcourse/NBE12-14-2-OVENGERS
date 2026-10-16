package com.ovengers.slotkey.stats.dto.response;

import com.ovengers.slotkey.space.entity.SpaceStatus;
import com.ovengers.slotkey.stats.entity.RankingMetric;
import com.ovengers.slotkey.stats.entity.RankingPeriodType;
import com.ovengers.slotkey.stats.entity.RankingStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 일반 회원용 공간 랭킹. 관리자용 {@link SpaceRankingResponse}에서 순매출만 뺀 모양이다. */
public record PublicSpaceRankingResponse(
        RankingPeriodType period,
        LocalDate periodStart,
        LocalDate periodEnd,
        RankingMetric metric,
        RankingStatus status,
        LocalDateTime confirmAt,
        LocalDateTime computedAt,
        List<Item> items
) {

    public record Item(
            int rank,
            Integer previousRank,
            Long spaceId,
            String spaceName,
            String location,
            SpaceStatus spaceStatus,
            int reservationCount,
            int slotCount
    ) {
    }

    public static PublicSpaceRankingResponse from(SpaceRankingResponse source) {
        List<Item> items = source.items().stream()
                .map(i -> new Item(i.rank(), i.previousRank(), i.spaceId(), i.spaceName(), i.location(),
                        i.spaceStatus(), i.reservationCount(), i.slotCount()))
                .toList();
        return new PublicSpaceRankingResponse(source.period(), source.periodStart(), source.periodEnd(),
                source.metric(), source.status(), source.confirmAt(), source.computedAt(), items);
    }
}
