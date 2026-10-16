package com.ovengers.slotkey.stats.service;

import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.space.entity.Space;
import com.ovengers.slotkey.space.entity.SpaceRegion;
import com.ovengers.slotkey.space.repository.SpaceRepository;
import com.ovengers.slotkey.stats.dto.response.LocationRankingItem;
import com.ovengers.slotkey.stats.dto.response.LocationRankingResponse;
import com.ovengers.slotkey.stats.dto.response.SpaceRankingItem;
import com.ovengers.slotkey.stats.dto.response.SpaceRankingResponse;
import com.ovengers.slotkey.stats.entity.RankingMetric;
import com.ovengers.slotkey.stats.entity.RankingPeriodType;
import com.ovengers.slotkey.stats.entity.RankingStatus;
import com.ovengers.slotkey.stats.entity.SpaceRankingSnapshot;
import com.ovengers.slotkey.stats.repository.SpaceRankingSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * 확정 스냅샷을 읽어 공간·지역 순위를 만든다. 순위(rank)는 저장하지 않고 여기서 지표별 동점 규칙으로 정렬해 매긴다.
 * 동점이어도 순위 번호는 겹치지 않는다. 근거: docs/decisions/space-ranking.md §2-6, §2-7, api-spec §14.
 */
@Service
@Transactional(readOnly = true)
public class SpaceRankingQueryService {

    static final int MAX_LIMIT = 50;

    private final SpaceRankingSnapshotRepository snapshotRepository;
    private final SpaceRepository spaceRepository;
    private final Clock clock;

    public SpaceRankingQueryService(SpaceRankingSnapshotRepository snapshotRepository,
                                    SpaceRepository spaceRepository,
                                    Clock clock) {
        this.snapshotRepository = snapshotRepository;
        this.spaceRepository = spaceRepository;
        this.clock = clock;
    }

    public SpaceRankingResponse getSpaceRanking(RankingPeriodType period, LocalDate date, RankingMetric metric,
                                                String location, int limit) {
        validateLimit(limit);
        if (location != null && !SpaceRegion.isSupported(location)) {
            throw validationFailed("지원하지 않는 지역입니다.");
        }
        PeriodView view = resolve(period, date);
        if (view.status != RankingStatus.CONFIRMED) {
            return new SpaceRankingResponse(period, view.start, view.end, metric, view.status,
                    view.confirmAt, null, List.of());
        }

        List<SpaceRankingSnapshot> ranked = sortedSpaceRows(period, view.start, metric, location);
        Map<Long, Integer> previousRanks = rankBySpaceId(sortedSpaceRows(period, previousStart(period, view.start),
                metric, location));

        List<SpaceRankingSnapshot> top = ranked.subList(0, Math.min(limit, ranked.size()));
        Map<Long, Space> spaces = new HashMap<>();
        spaceRepository.findAllById(top.stream().map(SpaceRankingSnapshot::getSpaceId).toList())
                .forEach(space -> spaces.put(space.getId(), space));

        List<SpaceRankingItem> items = new java.util.ArrayList<>();
        for (int i = 0; i < top.size(); i++) {
            SpaceRankingSnapshot row = top.get(i);
            Space space = spaces.get(row.getSpaceId());
            items.add(new SpaceRankingItem(
                    i + 1,
                    previousRanks.get(row.getSpaceId()),
                    row.getSpaceId(),
                    space == null ? null : space.getName(),
                    row.getLocation(),
                    space == null ? null : space.getStatus(),
                    row.getNetRevenue(),
                    row.getReservationCount(),
                    row.getSlotCount()));
        }
        return new SpaceRankingResponse(period, view.start, view.end, metric, view.status,
                null, view.computedAt, items);
    }

    public LocationRankingResponse getLocationRanking(RankingPeriodType period, LocalDate date,
                                                      RankingMetric metric, int limit) {
        validateLimit(limit);
        PeriodView view = resolve(period, date);
        if (view.status != RankingStatus.CONFIRMED) {
            return new LocationRankingResponse(period, view.start, view.end, metric, view.status,
                    view.confirmAt, null, List.of());
        }

        List<LocationTotal> ranked = sortedLocationTotals(period, view.start, metric);
        Map<String, Integer> previousRanks = new HashMap<>();
        List<LocationTotal> previous = sortedLocationTotals(period, previousStart(period, view.start), metric);
        for (int i = 0; i < previous.size(); i++) {
            previousRanks.put(previous.get(i).location, i + 1);
        }

        List<LocationRankingItem> items = new java.util.ArrayList<>();
        for (int i = 0; i < Math.min(limit, ranked.size()); i++) {
            LocationTotal total = ranked.get(i);
            items.add(new LocationRankingItem(i + 1, previousRanks.get(total.location), total.location,
                    total.spaceCount, total.netRevenue, total.reservationCount, total.slotCount));
        }
        return new LocationRankingResponse(period, view.start, view.end, metric, view.status,
                null, view.computedAt, items);
    }

    // ---------- 기간 상태 ----------

    private PeriodView resolve(RankingPeriodType period, LocalDate date) {
        LocalDate today = LocalDate.now(clock);
        LocalDate start = period.startOf(date);
        LocalDate end = period.endOf(date);

        if (start.isAfter(period.startOf(today))) {
            throw validationFailed("현재 기간보다 미래의 날짜는 조회할 수 없습니다.");
        }
        if (end.isAfter(today)) {
            return new PeriodView(start, end, RankingStatus.PENDING, end.atStartOfDay(), null);
        }

        List<SpaceRankingSnapshot> rows = snapshotRepository.findByPeriodTypeAndPeriodStart(period, start);
        if (!rows.isEmpty()) {
            LocalDateTime computedAt = rows.stream().map(SpaceRankingSnapshot::getComputedAt)
                    .max(Comparator.naturalOrder()).orElse(null);
            return new PeriodView(start, end, RankingStatus.CONFIRMED, null, computedAt);
        }
        // 행이 없으면 "거래가 없던 기간"과 "확정 기록이 시작되기 전"을 구분해야 한다.
        // 해당 종류의 가장 이른 확정 기간보다 앞이거나 기록이 전혀 없으면 UNAVAILABLE이다.
        boolean beforeRecords = snapshotRepository.findEarliestPeriodStart(period)
                .map(start::isBefore)
                .orElse(true);
        return new PeriodView(start, end, beforeRecords ? RankingStatus.UNAVAILABLE : RankingStatus.CONFIRMED,
                null, null);
    }

    private static LocalDate previousStart(RankingPeriodType period, LocalDate start) {
        return period.startOf(start.minusDays(1));
    }

    // ---------- 정렬 ----------

    private List<SpaceRankingSnapshot> sortedSpaceRows(RankingPeriodType period, LocalDate start,
                                                       RankingMetric metric, String location) {
        return snapshotRepository.findByPeriodTypeAndPeriodStart(period, start).stream()
                .filter(row -> location == null || location.equals(row.getLocation()))
                .sorted(spaceOrder(metric))
                .toList();
    }

    private static Map<Long, Integer> rankBySpaceId(List<SpaceRankingSnapshot> sorted) {
        Map<Long, Integer> ranks = new HashMap<>();
        for (int i = 0; i < sorted.size(); i++) {
            ranks.put(sorted.get(i).getSpaceId(), i + 1);
        }
        return ranks;
    }

    /** NET_REVENUE: 순매출→건수→spaceId, RESERVATION_COUNT: 건수→순매출→spaceId, SLOT_COUNT: 슬롯→건수→spaceId. */
    private static Comparator<SpaceRankingSnapshot> spaceOrder(RankingMetric metric) {
        Comparator<SpaceRankingSnapshot> revenue = Comparator.comparingLong(SpaceRankingSnapshot::getNetRevenue).reversed();
        Comparator<SpaceRankingSnapshot> count = Comparator.comparingInt(SpaceRankingSnapshot::getReservationCount).reversed();
        Comparator<SpaceRankingSnapshot> slots = Comparator.comparingInt(SpaceRankingSnapshot::getSlotCount).reversed();
        Comparator<SpaceRankingSnapshot> id = Comparator.comparing(SpaceRankingSnapshot::getSpaceId);
        return switch (metric) {
            case NET_REVENUE -> revenue.thenComparing(count).thenComparing(id);
            case RESERVATION_COUNT -> count.thenComparing(revenue).thenComparing(id);
            case SLOT_COUNT -> slots.thenComparing(count).thenComparing(id);
        };
    }

    private List<LocationTotal> sortedLocationTotals(RankingPeriodType period, LocalDate start, RankingMetric metric) {
        Map<String, LocationTotal> totals = new TreeMap<>();
        for (SpaceRankingSnapshot row : snapshotRepository.findByPeriodTypeAndPeriodStart(period, start)) {
            totals.merge(row.getLocation(), LocationTotal.of(row), LocationTotal::plus);
        }
        return totals.values().stream().sorted(locationOrder(metric)).toList();
    }

    /** 공간 규칙과 같고, 마지막 키만 spaceId 대신 location 가나다순이다. */
    private static Comparator<LocationTotal> locationOrder(RankingMetric metric) {
        Comparator<LocationTotal> revenue = Comparator.comparingLong((LocationTotal t) -> t.netRevenue).reversed();
        Comparator<LocationTotal> count = Comparator.comparingInt((LocationTotal t) -> t.reservationCount).reversed();
        Comparator<LocationTotal> slots = Comparator.comparingInt((LocationTotal t) -> t.slotCount).reversed();
        Comparator<LocationTotal> name = Comparator.comparing((Function<LocationTotal, String>) t -> t.location);
        return switch (metric) {
            case NET_REVENUE -> revenue.thenComparing(count).thenComparing(name);
            case RESERVATION_COUNT -> count.thenComparing(revenue).thenComparing(name);
            case SLOT_COUNT -> slots.thenComparing(count).thenComparing(name);
        };
    }

    // ---------- 검증·내부 타입 ----------

    private static void validateLimit(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw validationFailed("limit은 1 이상 " + MAX_LIMIT + " 이하여야 합니다.");
        }
    }

    private static BusinessException validationFailed(String message) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED, message);
    }

    private record PeriodView(LocalDate start, LocalDate end, RankingStatus status,
                              LocalDateTime confirmAt, LocalDateTime computedAt) {
    }

    private record LocationTotal(String location, int spaceCount, long netRevenue, int reservationCount, int slotCount) {
        static LocationTotal of(SpaceRankingSnapshot row) {
            return new LocationTotal(row.getLocation(), 1, row.getNetRevenue(), row.getReservationCount(), row.getSlotCount());
        }

        LocationTotal plus(LocationTotal other) {
            return new LocationTotal(location, spaceCount + other.spaceCount, netRevenue + other.netRevenue,
                    reservationCount + other.reservationCount, slotCount + other.slotCount);
        }
    }
}
