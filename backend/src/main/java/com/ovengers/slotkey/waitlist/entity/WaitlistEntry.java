package com.ovengers.slotkey.waitlist.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;

/**
 * 대기 신청 1건. 점유 중인 구간에 "자리가 비면 알려달라"고 걸어두는 기록이며,
 * 예약 성사를 보장하지 않는다(docs/plans/waitlist-outbox/00-waitlist-outbox-plan.md §2 — 전원 알림 + 선착순).
 *
 * member/space에 FK를 걸지 않고 id만 들고 있다(01 문서 "데이터와 API").
 * `@DynamicUpdate`: 바뀐 컬럼만 UPDATE한다. 없으면 취소(`cancel()`) flush가 전체 컬럼을 쓰면서, 그 사이
 * 컨슈머가 기록한 last_notified_at을 읽은 시점의 값(null)으로 되돌린다(lost update, step-1-review.md M3).
 * 알림 시각 기록 쪽은 `WaitlistEntryRepository.markNotified`(조건부 UPDATE)가 맡는다.
 *
 * 신규 코드이므로 Lombok을 쓰지 않는다(AGENTS.md).
 */
@Entity
@DynamicUpdate
@Table(name = "waitlist_entry")
public class WaitlistEntry {

    /** active 컬럼이 WAITING에서 갖는 값. UNIQUE 부분 적용에만 쓰이므로 1 하나만 사용한다. */
    private static final Integer ACTIVE = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "space_id", nullable = false)
    private Long spaceId;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WaitlistStatus status;

    /**
     * 활성 표시. WAITING이면 1, 그 밖의 상태면 null이다.
     * UNIQUE(member_id, space_id, start_time, end_time, active)의 마지막 칸이며,
     * MySQL이 NULL을 서로 다른 값으로 보는 성질을 이용해 "활성 1건 + 취소 이력 여러 건"을 만든다
     * (door_access_token의 active_reservation_id와 같은 방식).
     */
    @Column(name = "active")
    private Integer active;

    @Column(name = "last_notified_at")
    private LocalDateTime lastNotifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected WaitlistEntry() {
    }

    private WaitlistEntry(Long memberId, Long spaceId, LocalDateTime startTime, LocalDateTime endTime,
                          WaitlistStatus status, Integer active, LocalDateTime createdAt) {
        this.memberId = memberId;
        this.spaceId = spaceId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.active = active;
        this.createdAt = createdAt;
    }

    public static WaitlistEntry waiting(Long memberId, Long spaceId,
                                        LocalDateTime startTime, LocalDateTime endTime, LocalDateTime now) {
        return new WaitlistEntry(memberId, spaceId, startTime, endTime, WaitlistStatus.WAITING, ACTIVE, now);
    }

    /**
     * 본인 취소. 이미 WAITING이 아니면 아무것도 하지 않는다(취소 재요청을 그대로 성공 처리).
     * active를 NULL로 비워 같은 구간을 다시 신청할 수 있게 하고, 행은 이력으로 남긴다.
     */
    public void cancel() {
        if (this.status != WaitlistStatus.WAITING) {
            return;
        }
        this.status = WaitlistStatus.CANCELLED;
        this.active = null;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getSpaceId() {
        return spaceId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public WaitlistStatus getStatus() {
        return status;
    }

    public Integer getActive() {
        return active;
    }

    public LocalDateTime getLastNotifiedAt() {
        return lastNotifiedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
