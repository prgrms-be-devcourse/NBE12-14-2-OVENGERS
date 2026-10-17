package com.ovengers.slotkey.stats.repository;

import com.ovengers.slotkey.stats.entity.RankingPeriodType;
import com.ovengers.slotkey.stats.entity.SpaceRankingSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SpaceRankingSnapshotRepository extends JpaRepository<SpaceRankingSnapshot, Long> {

    boolean existsByPeriodTypeAndPeriodStart(RankingPeriodType periodType, LocalDate periodStart);

    List<SpaceRankingSnapshot> findByPeriodTypeAndPeriodStart(RankingPeriodType periodType, LocalDate periodStart);

    /** 해당 기간 종류의 확정 기록이 시작된 가장 이른 기간. 기록이 없으면 비어 있다. */
    @Query("SELECT MIN(s.periodStart) FROM SpaceRankingSnapshot s WHERE s.periodType = :periodType")
    Optional<LocalDate> findEarliestPeriodStart(@Param("periodType") RankingPeriodType periodType);

    /**
     * 같은 기간의 기존 행을 즉시 지운다. 파생 delete는 flush 순서상 INSERT보다 늦게 실행되어
     * UNIQUE(period_type, period_start, space_id)에 걸리므로 벌크 쿼리를 쓴다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM SpaceRankingSnapshot s WHERE s.periodType = :periodType AND s.periodStart = :periodStart")
    int deleteByPeriod(@Param("periodType") RankingPeriodType periodType,
                       @Param("periodStart") LocalDate periodStart);
}
