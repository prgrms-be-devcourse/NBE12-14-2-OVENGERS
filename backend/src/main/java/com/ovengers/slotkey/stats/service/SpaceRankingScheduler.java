package com.ovengers.slotkey.stats.service;

import com.ovengers.slotkey.stats.entity.RankingPeriodType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * 매일 00:01(KST)에 방금 끝난 기간을 확정한다: 일간은 매일, 주간은 월요일, 월간은 1일.
 * 서버 시작 시와 매일 확정 뒤에는 빠진 기간을 보충한다. 단일 서버를 전제로 한다(결정 문서 §6).
 */
@Component
@ConditionalOnProperty(
        prefix = "slotkey.scheduler.space-ranking",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class SpaceRankingScheduler {

    private static final Logger log = LoggerFactory.getLogger(SpaceRankingScheduler.class);

    private final SpaceRankingConfirmService confirmService;
    private final SpaceRankingBackfillService backfillService;
    private final Clock clock;

    public SpaceRankingScheduler(SpaceRankingConfirmService confirmService,
                                 SpaceRankingBackfillService backfillService,
                                 Clock clock) {
        this.confirmService = confirmService;
        this.backfillService = backfillService;
        this.clock = clock;
    }

    @Scheduled(cron = "0 1 0 * * *", zone = "Asia/Seoul")
    public void runDaily() {
        LocalDate today = LocalDate.now(clock);
        LocalDate yesterday = today.minusDays(1);

        confirmSafely(RankingPeriodType.DAILY, yesterday);
        if (today.getDayOfWeek() == DayOfWeek.MONDAY) {
            confirmSafely(RankingPeriodType.WEEKLY, yesterday);
        }
        if (today.getDayOfMonth() == 1) {
            confirmSafely(RankingPeriodType.MONTHLY, yesterday);
        }
        backfillSafely();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        backfillSafely();
    }

    private void confirmSafely(RankingPeriodType type, LocalDate dateInPeriod) {
        try {
            confirmService.confirm(type, dateInPeriod);
        } catch (RuntimeException e) {
            log.error("랭킹 확정 실패: type={}, date={}", type, dateInPeriod, e);
        }
    }

    private void backfillSafely() {
        try {
            backfillService.backfillMissing();
        } catch (RuntimeException e) {
            log.error("랭킹 보충 실패", e);
        }
    }
}
