package com.ovengers.slotkey.stats.service;

import com.ovengers.slotkey.stats.entity.RankingPeriodType;
import com.ovengers.slotkey.stats.repository.SpaceRankingSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 서버가 꺼져 있었거나 계산이 실패해 확정 행이 없는 "끝난 기간"을 찾아 채운다.
 * 이미 행이 있는 기간은 다시 계산하지 않는다(확정값 유지). 행이 하나도 없는 기간은 거래가 없던 기간과
 * 계산을 못 한 기간을 구분할 수 없어 매번 다시 계산 대상이 된다(결과는 같고 비용은 쿼리 3개).
 * 각 기간은 {@link SpaceRankingConfirmService}가 자기 트랜잭션으로 처리하므로 이 클래스는 트랜잭션을 열지 않는다.
 */
@Service
public class SpaceRankingBackfillService {

    private static final Logger log = LoggerFactory.getLogger(SpaceRankingBackfillService.class);

    private final SpaceRankingConfirmService confirmService;
    private final SpaceRankingSnapshotRepository snapshotRepository;
    private final Clock clock;
    private final int backfillDays;

    public SpaceRankingBackfillService(
            SpaceRankingConfirmService confirmService,
            SpaceRankingSnapshotRepository snapshotRepository,
            Clock clock,
            @Value("${slotkey.scheduler.space-ranking.backfill-days:31}") int backfillDays) {
        this.confirmService = confirmService;
        this.snapshotRepository = snapshotRepository;
        this.clock = clock;
        this.backfillDays = backfillDays;
    }

    /** @return 이번 호출에서 계산한 기간 수(행이 0개로 끝난 기간 포함) */
    public int backfillMissing() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        LocalDate windowStart = today.minusDays(backfillDays);
        int computed = 0;

        for (RankingPeriodType type : RankingPeriodType.values()) {
            // windowStart를 포함하는 기간부터, 끝 날짜 00:01이 지난 기간만
            for (LocalDate periodStart = type.startOf(windowStart);
                 SpaceRankingConfirmService.isConfirmable(type.endOf(periodStart), now);
                 periodStart = type.endOf(periodStart)) {
                if (snapshotRepository.existsByPeriodTypeAndPeriodStart(type, periodStart)) {
                    continue;
                }
                try {
                    confirmService.confirm(type, periodStart);
                    computed++;
                } catch (RuntimeException e) {
                    log.error("랭킹 보충 실패: type={}, periodStart={}", type, periodStart, e);
                }
            }
        }
        return computed;
    }
}
