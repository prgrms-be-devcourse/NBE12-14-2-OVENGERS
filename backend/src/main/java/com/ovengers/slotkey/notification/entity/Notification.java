package com.ovengers.slotkey.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 대기자에게 보내는 "자리가 났다" 알림 1건. member/waitlist_entry에 FK를 걸지 않고 id만 들고 있다.
 * (event_id, waitlist_entry_id)가 UNIQUE라 같은 이벤트로 같은 대기 항목에 두 번 생기지 않는다.
 * readAt이 null이면 안 읽은 알림이다 — 읽음 처리는 이번 범위가 아니다.
 *
 * 신규 코드이므로 Lombok을 쓰지 않는다(AGENTS.md).
 */
@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "waitlist_entry_id", nullable = false)
    private Long waitlistEntryId;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Notification() {
    }

    public Notification(Long memberId, Long waitlistEntryId, String eventId, String message,
                        LocalDateTime createdAt) {
        this.memberId = memberId;
        this.waitlistEntryId = waitlistEntryId;
        this.eventId = eventId;
        this.message = message;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getWaitlistEntryId() {
        return waitlistEntryId;
    }

    public String getEventId() {
        return eventId;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
