package com.ovengers.slotkey.stats.dto.response;

import com.ovengers.slotkey.stats.entity.RankingMetric;
import com.ovengers.slotkey.stats.entity.RankingPeriodType;
import com.ovengers.slotkey.stats.entity.RankingStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 일반 회원용 지역 랭킹. 관리자용 {@link LocationRankingResponse}에서 순매출만 뺀 모양이다. */
public record PublicLocationRankingResponse(
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
            String location,
            int spaceCount,
            int reservationCount,
            int slotCount
    ) {
    }

    public static PublicLocationRankingResponse from(LocationRankingResponse source) {
        List<Item> items = source.items().stream()
                .map(i -> new Item(i.rank(), i.previousRank(), i.location(), i.spaceCount(),
                        i.reservationCount(), i.slotCount()))
                .toList();
        return new PublicLocationRankingResponse(source.period(), source.periodStart(), source.periodEnd(),
                source.metric(), source.status(), source.confirmAt(), source.computedAt(), items);
    }
}
