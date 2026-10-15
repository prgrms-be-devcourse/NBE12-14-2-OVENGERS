package com.ovengers.slotkey.waitlist.repository;

import com.ovengers.slotkey.waitlist.entity.WaitlistEntry;
import com.ovengers.slotkey.waitlist.entity.WaitlistStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, Long> {

    /**
     * 활성 대기 건수. 3건 제한 검사용이며 동시 요청 시 초과를 수용한다(01 문서 §1).
     *
     * "활성"의 기준은 `status = WAITING AND startTime > now`다 — `EXPIRED` 전이가 4단계 범위라
     * 구간이 지난 WAITING 행이 남는데, 그 행이 제한을 영구히 차지하면 미래 구간을 신청할 수 없게 된다
     * (step-1-review.md H1). 시작 시각을 기준으로 삼은 이유는 알림 조건과 `ReservationTimePolicy`가
     * 모두 시작 시각 기준이기 때문이다.
     */
    long countByMemberIdAndStatusAndStartTimeAfter(Long memberId, WaitlistStatus status, LocalDateTime now);

    /** 내 대기 목록(활성만 — 위와 같은 기준). 신청 순서대로 보여주므로 id 오름차순이다. */
    List<WaitlistEntry> findAllByMemberIdAndStatusAndStartTimeAfterOrderByIdAsc(
            Long memberId, WaitlistStatus status, LocalDateTime now);

    /**
     * 신청 순서 계산용. 같은 공간에서 구간이 겹치는(startTime < :endTime AND endTime > :startTime)
     * 활성 WAITING 중 나보다 id가 작은 건수다. 여기에 1을 더한 값이 안내용 순서다(01 문서 §1).
     * 활성 판정은 위 두 메서드와 같은 기준(`status = WAITING AND startTime > :now`)이다.
     */
    @Query("SELECT COUNT(w) FROM WaitlistEntry w " +
            "WHERE w.spaceId = :spaceId AND w.status = :status AND w.startTime > :now " +
            "AND w.startTime < :endTime AND w.endTime > :startTime " +
            "AND w.id < :id")
    long countOverlappingAhead(
            @Param("spaceId") Long spaceId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("id") Long id,
            @Param("status") WaitlistStatus status,
            @Param("now") LocalDateTime now);
}
