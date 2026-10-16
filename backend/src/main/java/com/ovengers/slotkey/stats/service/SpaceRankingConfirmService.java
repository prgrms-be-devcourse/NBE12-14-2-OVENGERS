package com.ovengers.slotkey.stats.service;

import com.ovengers.slotkey.stats.entity.RankingPeriodType;
import com.ovengers.slotkey.stats.entity.SpaceRankingSnapshot;
import com.ovengers.slotkey.stats.repository.SpaceRankingAggregate;
import com.ovengers.slotkey.stats.repository.SpaceRankingAggregationRepository;
import com.ovengers.slotkey.stats.repository.SpaceRankingSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 끝난 기간의 공간별 집계를 계산해 확정값(space_ranking_snapshot)으로 저장한다.
 * 같은 기간을 다시 계산하면 그 기간의 기존 행을 지우고 다시 넣으므로 결과가 중복되지 않는다(멱등).
 * 근거: docs/decisions/space-ranking.md. 스케줄링·빠진 기간 보충은 단계 4에서 이 서비스를 호출한다.
 */
@Service
public class SpaceRankingConfirmService {

    /** 계산 시작 시각. 기간 끝 날짜의 이 시각부터 확정 대상이다(커밋 지연 완충). */
    static final java.time.LocalTime CONFIRM_TIME = java.time.LocalTime.of(0, 1);

    private final SpaceRankingAggregationRepository aggregationRepository;
    private final SpaceRankingSnapshotRepository snapshotRepository;
    private final Clock clock;

    public SpaceRankingConfirmService(SpaceRankingAggregationRepository aggregationRepository,
                                      SpaceRankingSnapshotRepository snapshotRepository,
                                      Clock clock) {
        this.aggregationRepository = aggregationRepository;
        this.snapshotRepository = snapshotRepository;
        this.clock = clock;
    }

    /** 기간 끝 날짜 00:01 이후면 확정 대상이다. 보충도 같은 기준을 쓴다. */
    static boolean isConfirmable(LocalDate periodEnd, LocalDateTime now) {
        return !now.isBefore(periodEnd.atTime(CONFIRM_TIME));
    }

    /**
     * @param dateInPeriod 확정할 기간에 속한 아무 날짜(KST)
     * @return 저장한 행 수
     * @throws IllegalArgumentException 기간이 아직 끝나지 않은 경우(기간 끝 날짜 00:01 이후에만 확정 가능)
     */
    @Transactional
    public int confirm(RankingPeriodType periodType, LocalDate dateInPeriod) {
        LocalDate periodStart = periodType.startOf(dateInPeriod);
        LocalDate periodEnd = periodType.endOf(dateInPeriod);
        LocalDateTime now = LocalDateTime.now(clock);

        if (!isConfirmable(periodEnd, now)) {
            throw new IllegalArgumentException(
                    "아직 끝나지 않은 기간은 확정할 수 없습니다: %s %s ~ %s".formatted(periodType, periodStart, periodEnd));
        }

        List<SpaceRankingAggregate> aggregates =
                aggregationRepository.aggregate(periodStart.atStartOfDay(), periodEnd.atStartOfDay());

        snapshotRepository.deleteByPeriod(periodType, periodStart);
        List<SpaceRankingSnapshot> snapshots = aggregates.stream()
                .map(a -> new SpaceRankingSnapshot(periodType, periodStart, a.spaceId(), a.location(),
                        a.netRevenue(), a.reservationCount(), a.slotCount(), now))
                .toList();
        snapshotRepository.saveAll(snapshots);
        return snapshots.size();
    }
}
