package com.ovengers.slotkey.stats.service;

import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.space.entity.SpaceStatus;
import com.ovengers.slotkey.stats.dto.response.LocationRankingItem;
import com.ovengers.slotkey.stats.dto.response.LocationRankingResponse;
import com.ovengers.slotkey.stats.dto.response.SpaceRankingItem;
import com.ovengers.slotkey.stats.dto.response.SpaceRankingResponse;
import com.ovengers.slotkey.stats.entity.RankingMetric;
import com.ovengers.slotkey.stats.entity.RankingPeriodType;
import com.ovengers.slotkey.stats.entity.RankingStatus;
import com.ovengers.slotkey.stats.entity.SpaceRankingSnapshot;
import com.ovengers.slotkey.stats.repository.SpaceRankingSnapshotRepository;
import com.ovengers.slotkey.support.ReservationIntegrationTestSupport;
import com.ovengers.slotkey.support.SettableClockConfig;
import com.ovengers.slotkey.support.SettableClockConfig.SettableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 조회 서비스(단계 5)의 순위·상태 규칙. 기준 시각은 2026-10-16(금) 10:00 KST다.
 * 스냅샷 행은 직접 저장하고, 순위는 저장하지 않고 조회 시 계산하는지 확인한다.
 */
@Import(SettableClockConfig.class)
class SpaceRankingQueryServiceIntegrationTest extends ReservationIntegrationTestSupport {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 14);
    private static final LocalDate PREVIOUS_DAY = LocalDate.of(2026, 10, 13);

    @Autowired
    private SpaceRankingQueryService queryService;
    @Autowired
    private SpaceRankingSnapshotRepository snapshotRepository;
    @Autowired
    private SettableClock settableClock;

    @BeforeEach
    void setUp() {
        settableClock.set(ZonedDateTime.of(2026, 10, 16, 10, 0, 0, 0, SettableClockConfig.KST));
    }

    @AfterEach
    void cleanSnapshots() {
        jdbcTemplate.update("DELETE FROM space_ranking_snapshot");
    }

    // ---------- 동점 규칙과 순위 ----------

    @Test
    @DisplayName("순매출 기준 동점은 예약 건수, 그다음 spaceId 오름차순이고 순위 번호는 겹치지 않는다")
    void netRevenueTieBreak() {
        Long a = space("A", "판교");
        Long b = space("B", "판교");
        Long c = space("C", "판교");
        Long d = space("D", "판교");
        snap(RankingPeriodType.DAILY, DAY, a, "판교", 100, 2, 2);
        snap(RankingPeriodType.DAILY, DAY, b, "판교", 100, 3, 3);
        snap(RankingPeriodType.DAILY, DAY, c, "판교", 100, 3, 3);
        snap(RankingPeriodType.DAILY, DAY, d, "판교", 50, 9, 9);

        SpaceRankingResponse response = space(RankingMetric.NET_REVENUE, null, 10);

        assertThat(response.items()).extracting(SpaceRankingItem::spaceId).containsExactly(b, c, a, d);
        assertThat(response.items()).extracting(SpaceRankingItem::rank).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("예약 건수 기준 동점은 순매출, 그다음 spaceId 오름차순이다")
    void reservationCountTieBreak() {
        Long a = space("A", "판교");
        Long b = space("B", "판교");
        Long c = space("C", "판교");
        Long d = space("D", "판교");
        snap(RankingPeriodType.DAILY, DAY, a, "판교", 100, 5, 5);
        snap(RankingPeriodType.DAILY, DAY, b, "판교", 300, 5, 5);
        snap(RankingPeriodType.DAILY, DAY, c, "판교", 300, 5, 5);
        snap(RankingPeriodType.DAILY, DAY, d, "판교", 999, 4, 4);

        SpaceRankingResponse response = space(RankingMetric.RESERVATION_COUNT, null, 10);

        assertThat(response.items()).extracting(SpaceRankingItem::spaceId).containsExactly(b, c, a, d);
    }

    @Test
    @DisplayName("슬롯 수 기준 동점은 예약 건수, 그다음 spaceId 오름차순이다")
    void slotCountTieBreak() {
        Long a = space("A", "판교");
        Long b = space("B", "판교");
        Long c = space("C", "판교");
        Long d = space("D", "판교");
        snap(RankingPeriodType.DAILY, DAY, a, "판교", 900, 1, 10);
        snap(RankingPeriodType.DAILY, DAY, b, "판교", 100, 3, 10);
        snap(RankingPeriodType.DAILY, DAY, c, "판교", 100, 3, 10);
        snap(RankingPeriodType.DAILY, DAY, d, "판교", 999, 9, 9);

        SpaceRankingResponse response = space(RankingMetric.SLOT_COUNT, null, 10);

        assertThat(response.items()).extracting(SpaceRankingItem::spaceId).containsExactly(b, c, a, d);
    }

    @Test
    @DisplayName("세 지표는 정렬 기준과 상관없이 항상 모두 내려가고 공간 이름·상태가 붙는다")
    void itemCarriesAllMetricsAndSpaceInfo() {
        Long inactive = space("폐쇄된 룸", "하남");
        jdbcTemplate.update("UPDATE spaces SET status = 'INACTIVE' WHERE id = ?", inactive);
        snap(RankingPeriodType.DAILY, DAY, inactive, "하남", 12_000, 3, 7);

        SpaceRankingItem item = space(RankingMetric.SLOT_COUNT, null, 10).items().get(0);

        assertThat(item.spaceName()).isEqualTo("폐쇄된 룸");
        assertThat(item.spaceStatus()).isEqualTo(SpaceStatus.INACTIVE);
        assertThat(item.location()).isEqualTo("하남");
        assertThat(item.netRevenue()).isEqualTo(12_000L);
        assertThat(item.reservationCount()).isEqualTo(3);
        assertThat(item.slotCount()).isEqualTo(7);
    }

    @Test
    @DisplayName("location 필터는 해당 지역만, limit은 순위 계산 뒤 상위 N개만 돌려준다")
    void locationFilterAndLimit() {
        Long p1 = space("P1", "판교");
        Long p2 = space("P2", "판교");
        Long p3 = space("P3", "판교");
        Long g1 = space("G1", "강남");
        snap(RankingPeriodType.DAILY, DAY, g1, "강남", 10_000, 9, 9);
        snap(RankingPeriodType.DAILY, DAY, p1, "판교", 300, 1, 1);
        snap(RankingPeriodType.DAILY, DAY, p2, "판교", 200, 1, 1);
        snap(RankingPeriodType.DAILY, DAY, p3, "판교", 100, 1, 1);

        SpaceRankingResponse response = space(RankingMetric.NET_REVENUE, "판교", 2);

        assertThat(response.items()).extracting(SpaceRankingItem::spaceId).containsExactly(p1, p2);
        assertThat(response.items()).extracting(SpaceRankingItem::rank).containsExactly(1, 2);
    }

    @Test
    @DisplayName("지역은 계산 당시 스냅샷의 location을 쓴다(공간이 나중에 지역을 바꿔도 과거 순위는 불변)")
    void usesLocationStoredInSnapshot() {
        Long moved = space("이사한 룸", "판교");
        snap(RankingPeriodType.DAILY, DAY, moved, "하남", 100, 1, 1);

        assertThat(space(RankingMetric.NET_REVENUE, "하남", 10).items()).hasSize(1);
        assertThat(space(RankingMetric.NET_REVENUE, "판교", 10).items()).isEmpty();
    }

    // ---------- 이전 기간 순위 ----------

    @Test
    @DisplayName("previousRank는 같은 기간 종류·지표·지역 필터의 직전 기간 순위이고, 없으면 null이다")
    void previousRank() {
        Long a = space("A", "판교");
        Long b = space("B", "판교");
        Long c = space("C", "판교");
        snap(RankingPeriodType.DAILY, PREVIOUS_DAY, a, "판교", 100, 1, 1);
        snap(RankingPeriodType.DAILY, PREVIOUS_DAY, b, "판교", 300, 1, 1);
        snap(RankingPeriodType.DAILY, DAY, a, "판교", 500, 1, 1);
        snap(RankingPeriodType.DAILY, DAY, b, "판교", 400, 1, 1);
        snap(RankingPeriodType.DAILY, DAY, c, "판교", 300, 1, 1);

        List<SpaceRankingItem> items = space(RankingMetric.NET_REVENUE, null, 10).items();

        assertThat(items).extracting(SpaceRankingItem::spaceId).containsExactly(a, b, c);
        assertThat(items).extracting(SpaceRankingItem::previousRank).containsExactly(2, 1, null);
    }

    @Test
    @DisplayName("previousRank는 limit과 무관하게 직전 기간 전체 순위에서 구한다")
    void previousRankIgnoresLimit() {
        Long a = space("A", "판교");
        Long b = space("B", "판교");
        snap(RankingPeriodType.DAILY, PREVIOUS_DAY, b, "판교", 900, 1, 1);
        snap(RankingPeriodType.DAILY, PREVIOUS_DAY, a, "판교", 100, 1, 1);
        snap(RankingPeriodType.DAILY, DAY, a, "판교", 500, 1, 1);

        SpaceRankingItem top = space(RankingMetric.NET_REVENUE, null, 1).items().get(0);

        assertThat(top.spaceId()).isEqualTo(a);
        assertThat(top.previousRank()).isEqualTo(2);
    }

    @Test
    @DisplayName("previousRank의 직전 기간은 지역 필터 안에서 계산한다")
    void previousRankUsesSameLocationFilter() {
        Long p = space("P", "판교");
        Long g = space("G", "강남");
        snap(RankingPeriodType.DAILY, PREVIOUS_DAY, g, "강남", 900, 1, 1);
        snap(RankingPeriodType.DAILY, PREVIOUS_DAY, p, "판교", 100, 1, 1);
        snap(RankingPeriodType.DAILY, DAY, p, "판교", 500, 1, 1);

        SpaceRankingItem item = space(RankingMetric.NET_REVENUE, "판교", 10).items().get(0);

        assertThat(item.previousRank()).isEqualTo(1);
    }

    @Test
    @DisplayName("직전 기간에 행이 없으면 모든 previousRank가 null이다")
    void previousRankNullWhenPreviousPeriodEmpty() {
        Long a = space("A", "판교");
        snap(RankingPeriodType.DAILY, DAY, a, "판교", 500, 1, 1);

        assertThat(space(RankingMetric.NET_REVENUE, null, 10).items().get(0).previousRank()).isNull();
    }

    @Test
    @DisplayName("주간·월간의 직전 기간은 지난 주·지난 달이다")
    void previousRankForWeeklyAndMonthly() {
        Long a = space("A", "판교");
        snap(RankingPeriodType.WEEKLY, LocalDate.of(2026, 9, 28), a, "판교", 100, 1, 1);
        snap(RankingPeriodType.WEEKLY, LocalDate.of(2026, 10, 5), a, "판교", 100, 1, 1);
        snap(RankingPeriodType.MONTHLY, LocalDate.of(2026, 8, 1), a, "판교", 100, 1, 1);
        snap(RankingPeriodType.MONTHLY, LocalDate.of(2026, 9, 1), a, "판교", 100, 1, 1);

        SpaceRankingResponse weekly = queryService.getSpaceRanking(RankingPeriodType.WEEKLY,
                LocalDate.of(2026, 10, 7), RankingMetric.NET_REVENUE, null, 10);
        SpaceRankingResponse monthly = queryService.getSpaceRanking(RankingPeriodType.MONTHLY,
                LocalDate.of(2026, 9, 30), RankingMetric.NET_REVENUE, null, 10);

        assertThat(weekly.periodStart()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(weekly.periodEnd()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(weekly.items().get(0).previousRank()).isEqualTo(1);
        assertThat(monthly.periodStart()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(monthly.periodEnd()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(monthly.items().get(0).previousRank()).isEqualTo(1);
    }

    // ---------- 상태 ----------

    @Test
    @DisplayName("확정된 기간은 CONFIRMED와 계산 완료 시각(computedAt)을 돌려주고 confirmAt은 없다")
    void confirmedStatus() {
        Long a = space("A", "판교");
        snap(RankingPeriodType.DAILY, DAY, a, "판교", 100, 1, 1);

        SpaceRankingResponse response = space(RankingMetric.NET_REVENUE, null, 10);

        assertThat(response.status()).isEqualTo(RankingStatus.CONFIRMED);
        assertThat(response.computedAt()).isEqualTo(LocalDateTime.of(2026, 10, 15, 0, 1));
        assertThat(response.confirmAt()).isNull();
        assertThat(response.periodStart()).isEqualTo(DAY);
        assertThat(response.periodEnd()).isEqualTo(DAY.plusDays(1));
        assertThat(response.metric()).isEqualTo(RankingMetric.NET_REVENUE);
        assertThat(response.period()).isEqualTo(RankingPeriodType.DAILY);
    }

    @Test
    @DisplayName("오늘·이번 주·이번 달을 포함하는 date는 PENDING이고 items는 비며 confirmAt은 기간 끝 00:00이다")
    void pendingForCurrentPeriods() {
        Long a = space("A", "판교");
        snap(RankingPeriodType.DAILY, LocalDate.of(2026, 10, 16), a, "판교", 100, 1, 1);   // 있어도 보여 주지 않는다

        SpaceRankingResponse daily = queryService.getSpaceRanking(RankingPeriodType.DAILY,
                LocalDate.of(2026, 10, 16), RankingMetric.NET_REVENUE, null, 10);
        SpaceRankingResponse weekly = queryService.getSpaceRanking(RankingPeriodType.WEEKLY,
                LocalDate.of(2026, 10, 18), RankingMetric.NET_REVENUE, null, 10);   // 이번 주의 미래 날짜
        SpaceRankingResponse monthly = queryService.getSpaceRanking(RankingPeriodType.MONTHLY,
                LocalDate.of(2026, 10, 31), RankingMetric.NET_REVENUE, null, 10);

        assertThat(daily.status()).isEqualTo(RankingStatus.PENDING);
        assertThat(daily.items()).isEmpty();
        assertThat(daily.computedAt()).isNull();
        assertThat(daily.confirmAt()).isEqualTo(LocalDateTime.of(2026, 10, 17, 0, 0));
        assertThat(weekly.status()).isEqualTo(RankingStatus.PENDING);
        assertThat(weekly.periodStart()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(weekly.confirmAt()).isEqualTo(LocalDateTime.of(2026, 10, 19, 0, 0));
        assertThat(monthly.status()).isEqualTo(RankingStatus.PENDING);
        assertThat(monthly.confirmAt()).isEqualTo(LocalDateTime.of(2026, 11, 1, 0, 0));
    }

    @Test
    @DisplayName("기간이 막 끝난 날(end == today)에 확정 행이 있으면 CONFIRMED로 순위를 내려준다")
    void confirmedWhenPeriodEndsToday() {
        Long a = space("A", "판교");
        // 어제 일간, 월요일 당일의 지난주, 1일 당일의 지난달
        snap(RankingPeriodType.DAILY, LocalDate.of(2026, 10, 16), a, "판교", 100, 1, 1);
        snap(RankingPeriodType.WEEKLY, LocalDate.of(2026, 10, 12), a, "판교", 100, 1, 1);
        snap(RankingPeriodType.MONTHLY, LocalDate.of(2026, 10, 1), a, "판교", 100, 1, 1);

        settableClock.set(ZonedDateTime.of(2026, 10, 17, 10, 0, 0, 0, SettableClockConfig.KST));
        SpaceRankingResponse daily = queryService.getSpaceRanking(RankingPeriodType.DAILY,
                LocalDate.of(2026, 10, 16), RankingMetric.NET_REVENUE, null, 10);
        settableClock.set(ZonedDateTime.of(2026, 10, 19, 10, 0, 0, 0, SettableClockConfig.KST));   // 월요일
        SpaceRankingResponse weekly = queryService.getSpaceRanking(RankingPeriodType.WEEKLY,
                LocalDate.of(2026, 10, 18), RankingMetric.NET_REVENUE, null, 10);
        settableClock.set(ZonedDateTime.of(2026, 11, 1, 10, 0, 0, 0, SettableClockConfig.KST));    // 1일
        SpaceRankingResponse monthly = queryService.getSpaceRanking(RankingPeriodType.MONTHLY,
                LocalDate.of(2026, 10, 31), RankingMetric.NET_REVENUE, null, 10);

        assertThat(daily.status()).isEqualTo(RankingStatus.CONFIRMED);
        assertThat(daily.items()).extracting(SpaceRankingItem::spaceId).containsExactly(a);
        assertThat(weekly.status()).isEqualTo(RankingStatus.CONFIRMED);
        assertThat(weekly.items()).hasSize(1);
        assertThat(monthly.status()).isEqualTo(RankingStatus.CONFIRMED);
        assertThat(monthly.items()).hasSize(1);
    }

    @Test
    @DisplayName("확정 전(기간 끝 날짜 00:00~00:01)에 행이 없으면 빈 CONFIRMED가 아니라 PENDING이고 00:01부터는 CONFIRMED다")
    void pendingUntilOneMinuteAfterPeriodEnd() {
        Long a = space("A", "판교");
        snap(RankingPeriodType.DAILY, LocalDate.of(2026, 10, 14), a, "판교", 100, 1, 1);   // 기록 범위 안으로 만든다

        settableClock.set(ZonedDateTime.of(2026, 10, 17, 0, 0, 30, 0, SettableClockConfig.KST));
        SpaceRankingResponse before = queryService.getSpaceRanking(RankingPeriodType.DAILY,
                LocalDate.of(2026, 10, 16), RankingMetric.NET_REVENUE, null, 10);
        settableClock.set(ZonedDateTime.of(2026, 10, 17, 0, 1, 0, 0, SettableClockConfig.KST));
        SpaceRankingResponse after = queryService.getSpaceRanking(RankingPeriodType.DAILY,
                LocalDate.of(2026, 10, 16), RankingMetric.NET_REVENUE, null, 10);

        assertThat(before.status()).isEqualTo(RankingStatus.PENDING);
        assertThat(before.items()).isEmpty();
        assertThat(before.confirmAt()).isEqualTo(LocalDateTime.of(2026, 10, 17, 0, 0));
        assertThat(after.status()).isEqualTo(RankingStatus.CONFIRMED);   // 행이 없으면 거래가 없던 기간
        assertThat(after.items()).isEmpty();
    }

    @Test
    @DisplayName("지난주·지난달도 월요일·1일 00:00~00:01에는 행이 없으면 PENDING이고 지역 랭킹도 같다")
    void pendingRightAfterWeekAndMonthEnd() {
        Long a = space("A", "판교");
        snap(RankingPeriodType.WEEKLY, LocalDate.of(2026, 10, 5), a, "판교", 100, 1, 1);
        snap(RankingPeriodType.MONTHLY, LocalDate.of(2026, 9, 1), a, "판교", 100, 1, 1);

        settableClock.set(ZonedDateTime.of(2026, 10, 19, 0, 0, 30, 0, SettableClockConfig.KST));
        SpaceRankingResponse weekly = queryService.getSpaceRanking(RankingPeriodType.WEEKLY,
                LocalDate.of(2026, 10, 18), RankingMetric.NET_REVENUE, null, 10);
        settableClock.set(ZonedDateTime.of(2026, 11, 1, 0, 0, 30, 0, SettableClockConfig.KST));
        LocationRankingResponse monthly = queryService.getLocationRanking(RankingPeriodType.MONTHLY,
                LocalDate.of(2026, 10, 31), RankingMetric.NET_REVENUE, 10);

        assertThat(weekly.status()).isEqualTo(RankingStatus.PENDING);
        assertThat(weekly.confirmAt()).isEqualTo(LocalDateTime.of(2026, 10, 19, 0, 0));
        assertThat(monthly.status()).isEqualTo(RankingStatus.PENDING);
        assertThat(monthly.confirmAt()).isEqualTo(LocalDateTime.of(2026, 11, 1, 0, 0));
    }

    @Test
    @DisplayName("현재 기간보다 미래인 date는 400 VALIDATION_FAILED이다")
    void futurePeriodIsRejected() {
        assertValidationFailed(() -> queryService.getSpaceRanking(RankingPeriodType.DAILY,
                LocalDate.of(2026, 10, 17), RankingMetric.NET_REVENUE, null, 10));
        assertValidationFailed(() -> queryService.getSpaceRanking(RankingPeriodType.WEEKLY,
                LocalDate.of(2026, 10, 19), RankingMetric.NET_REVENUE, null, 10));
        assertValidationFailed(() -> queryService.getSpaceRanking(RankingPeriodType.MONTHLY,
                LocalDate.of(2026, 11, 1), RankingMetric.NET_REVENUE, null, 10));
        assertValidationFailed(() -> queryService.getLocationRanking(RankingPeriodType.DAILY,
                LocalDate.of(2026, 10, 17), RankingMetric.NET_REVENUE, 10));
    }

    @Test
    @DisplayName("스냅샷이 시작된 시점보다 이전의 끝난 기간과, 스냅샷이 전혀 없는 기간 종류는 UNAVAILABLE이다")
    void unavailableBeforeSnapshotsStarted() {
        Long a = space("A", "판교");
        snap(RankingPeriodType.DAILY, LocalDate.of(2026, 10, 10), a, "판교", 100, 1, 1);

        SpaceRankingResponse beforeStart = queryService.getSpaceRanking(RankingPeriodType.DAILY,
                LocalDate.of(2026, 10, 5), RankingMetric.NET_REVENUE, null, 10);
        SpaceRankingResponse noWeeklyAtAll = queryService.getSpaceRanking(RankingPeriodType.WEEKLY,
                LocalDate.of(2026, 10, 5), RankingMetric.NET_REVENUE, null, 10);

        assertThat(beforeStart.status()).isEqualTo(RankingStatus.UNAVAILABLE);
        assertThat(beforeStart.items()).isEmpty();
        assertThat(noWeeklyAtAll.status()).isEqualTo(RankingStatus.UNAVAILABLE);
    }

    @Test
    @DisplayName("스냅샷 범위 안에서 행이 없는 끝난 기간(거래 없음)은 CONFIRMED이고 items가 비며 computedAt은 null이다")
    void emptyPeriodInsideCoveredRangeIsConfirmed() {
        Long a = space("A", "판교");
        snap(RankingPeriodType.DAILY, LocalDate.of(2026, 10, 10), a, "판교", 100, 1, 1);
        snap(RankingPeriodType.DAILY, LocalDate.of(2026, 10, 14), a, "판교", 100, 1, 1);

        SpaceRankingResponse response = queryService.getSpaceRanking(RankingPeriodType.DAILY,
                LocalDate.of(2026, 10, 12), RankingMetric.NET_REVENUE, null, 10);

        assertThat(response.status()).isEqualTo(RankingStatus.CONFIRMED);
        assertThat(response.items()).isEmpty();
        assertThat(response.computedAt()).isNull();
    }

    // ---------- 검증 ----------

    @Test
    @DisplayName("limit은 1~50만 허용하고 지원하지 않는 location은 400 VALIDATION_FAILED이다")
    void limitAndLocationValidation() {
        assertValidationFailed(() -> space(RankingMetric.NET_REVENUE, null, 0));
        assertValidationFailed(() -> space(RankingMetric.NET_REVENUE, null, 51));
        assertValidationFailed(() -> space(RankingMetric.NET_REVENUE, "서울", 10));
        assertThat(space(RankingMetric.NET_REVENUE, null, 1).status()).isNotNull();
        assertThat(space(RankingMetric.NET_REVENUE, null, 50).status()).isNotNull();
    }

    // ---------- 지역 랭킹 ----------

    @Test
    @DisplayName("지역 랭킹은 공간 행을 스냅샷의 location별로 합산하고 합계는 공간 행의 합과 일치한다")
    void locationRankingSumsSpaceRows() {
        Long p1 = space("P1", "판교");
        Long p2 = space("P2", "판교");
        Long g1 = space("G1", "강남");
        snap(RankingPeriodType.DAILY, DAY, p1, "판교", 100, 2, 4);
        snap(RankingPeriodType.DAILY, DAY, p2, "판교", 50, 1, 1);
        snap(RankingPeriodType.DAILY, DAY, g1, "강남", 120, 1, 2);

        LocationRankingResponse response = location(RankingMetric.NET_REVENUE, 10);

        assertThat(response.status()).isEqualTo(RankingStatus.CONFIRMED);
        assertThat(response.items()).extracting(LocationRankingItem::location).containsExactly("판교", "강남");
        LocationRankingItem pangyo = response.items().get(0);
        assertThat(pangyo.spaceCount()).isEqualTo(2);
        assertThat(pangyo.netRevenue()).isEqualTo(150L);
        assertThat(pangyo.reservationCount()).isEqualTo(3);
        assertThat(pangyo.slotCount()).isEqualTo(5);
        assertThat(response.items().stream().mapToLong(LocationRankingItem::netRevenue).sum()).isEqualTo(270L);
    }

    @Test
    @DisplayName("지역 랭킹 동점은 지표별 보조 기준 다음 location 가나다순이다")
    void locationRankingTieBreakEndsWithLocationName() {
        Long p = space("P", "판교");
        Long g = space("G", "강남");
        Long h = space("H", "하남");
        snap(RankingPeriodType.DAILY, DAY, p, "판교", 100, 1, 1);
        snap(RankingPeriodType.DAILY, DAY, g, "강남", 100, 1, 1);
        snap(RankingPeriodType.DAILY, DAY, h, "하남", 100, 1, 1);

        for (RankingMetric metric : RankingMetric.values()) {
            assertThat(location(metric, 10).items()).extracting(LocationRankingItem::location)
                    .containsExactly("강남", "판교", "하남");
        }
    }

    @Test
    @DisplayName("지역 랭킹에도 previousRank와 limit이 적용된다")
    void locationRankingPreviousRankAndLimit() {
        Long p = space("P", "판교");
        Long g = space("G", "강남");
        Long h = space("H", "하남");
        snap(RankingPeriodType.DAILY, PREVIOUS_DAY, h, "하남", 900, 1, 1);
        snap(RankingPeriodType.DAILY, PREVIOUS_DAY, p, "판교", 500, 1, 1);
        snap(RankingPeriodType.DAILY, DAY, p, "판교", 700, 1, 1);
        snap(RankingPeriodType.DAILY, DAY, g, "강남", 300, 1, 1);
        snap(RankingPeriodType.DAILY, DAY, h, "하남", 200, 1, 1);

        LocationRankingResponse response = location(RankingMetric.NET_REVENUE, 2);

        assertThat(response.items()).extracting(LocationRankingItem::location).containsExactly("판교", "강남");
        assertThat(response.items()).extracting(LocationRankingItem::previousRank).containsExactly(2, null);
    }

    @Test
    @DisplayName("지역 랭킹의 PENDING·UNAVAILABLE 규칙은 공간 랭킹과 같다")
    void locationRankingStatuses() {
        LocationRankingResponse pending = queryService.getLocationRanking(RankingPeriodType.DAILY,
                LocalDate.of(2026, 10, 16), RankingMetric.NET_REVENUE, 10);
        LocationRankingResponse unavailable = location(RankingMetric.NET_REVENUE, 10);

        assertThat(pending.status()).isEqualTo(RankingStatus.PENDING);
        assertThat(pending.confirmAt()).isEqualTo(LocalDateTime.of(2026, 10, 17, 0, 0));
        assertThat(unavailable.status()).isEqualTo(RankingStatus.UNAVAILABLE);
    }

    // ---------- helpers ----------

    private SpaceRankingResponse space(RankingMetric metric, String location, int limit) {
        return queryService.getSpaceRanking(RankingPeriodType.DAILY, DAY, metric, location, limit);
    }

    private LocationRankingResponse location(RankingMetric metric, int limit) {
        return queryService.getLocationRanking(RankingPeriodType.DAILY, DAY, metric, limit);
    }

    private Long space(String name, String location) {
        Long id = createSpace();
        jdbcTemplate.update("UPDATE spaces SET name = ?, location = ? WHERE id = ?", name, location, id);
        return id;
    }

    private void snap(RankingPeriodType type, LocalDate periodStart, Long spaceId, String location,
                      long revenue, int count, int slots) {
        snapshotRepository.save(new SpaceRankingSnapshot(type, periodStart, spaceId, location, revenue, count, slots,
                type.endOf(periodStart).atTime(0, 1)));
    }

    private static void assertValidationFailed(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }
}
