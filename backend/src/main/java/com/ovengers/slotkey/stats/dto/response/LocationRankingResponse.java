package com.ovengers.slotkey.stats.dto.response;

import com.ovengers.slotkey.stats.entity.RankingMetric;
import com.ovengers.slotkey.stats.entity.RankingPeriodType;
import com.ovengers.slotkey.stats.entity.RankingStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record LocationRankingResponse(
        RankingPeriodType period,
        LocalDate periodStart,
        LocalDate periodEnd,
        RankingMetric metric,
        RankingStatus status,
        LocalDateTime confirmAt,
        LocalDateTime computedAt,
        List<LocationRankingItem> items
) {
}
