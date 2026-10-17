package com.ovengers.slotkey.stats.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 끝난 기간의 공간별 확정 집계 한 줄. 순위는 저장하지 않는다(조회 시 계산). */
@Entity
@Table(name = "space_ranking_snapshot")
public class SpaceRankingSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 10)
    private RankingPeriodType periodType;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "space_id", nullable = false)
    private Long spaceId;

    /** 계산 당시의 spaces.location. 이후 공간 지역이 바뀌어도 과거 지역 랭킹은 바뀌지 않는다. */
    @Column(nullable = false)
    private String location;

    @Column(name = "net_revenue", nullable = false)
    private long netRevenue;

    @Column(name = "reservation_count", nullable = false)
    private int reservationCount;

    @Column(name = "slot_count", nullable = false)
    private int slotCount;

    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt;

    protected SpaceRankingSnapshot() {
    }

    public SpaceRankingSnapshot(RankingPeriodType periodType, LocalDate periodStart, Long spaceId, String location,
                                long netRevenue, int reservationCount, int slotCount, LocalDateTime computedAt) {
        this.periodType = periodType;
        this.periodStart = periodStart;
        this.spaceId = spaceId;
        this.location = location;
        this.netRevenue = netRevenue;
        this.reservationCount = reservationCount;
        this.slotCount = slotCount;
        this.computedAt = computedAt;
    }

    public Long getId() {
        return id;
    }

    public RankingPeriodType getPeriodType() {
        return periodType;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public Long getSpaceId() {
        return spaceId;
    }

    public String getLocation() {
        return location;
    }

    public long getNetRevenue() {
        return netRevenue;
    }

    public int getReservationCount() {
        return reservationCount;
    }

    public int getSlotCount() {
        return slotCount;
    }

    public LocalDateTime getComputedAt() {
        return computedAt;
    }
}
