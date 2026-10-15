-- 일시: 2026-10-16
-- 기능: 예약 대기열 4단계(자리 알림 저장·조회)
-- 참고: docs/plans/waitlist-outbox/01-waitlist-rules.md §2
--
-- FK를 걸지 않는다(member/waitlist_entry 잠금 순서 문제를 새로 만들지 않기 위함).
-- UNIQUE(event_id, waitlist_entry_id): 같은 이벤트로 같은 대기 항목에 알림이 두 번 생기지 않게 하는
-- 두 번째 방어선이다(첫 번째는 processed_event). read_at이 NULL이면 안 읽은 알림이다.
-- 읽음 처리 API는 이번 범위가 아니므로 read_at은 항상 NULL로 남는다.

CREATE TABLE notification (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id         BIGINT NOT NULL,
    waitlist_entry_id BIGINT NOT NULL,
    event_id          VARCHAR(36) NOT NULL,
    message           VARCHAR(500) NOT NULL,
    read_at           DATETIME NULL,
    created_at        DATETIME NOT NULL,
    CONSTRAINT uk_notification_event_entry UNIQUE (event_id, waitlist_entry_id),
    INDEX idx_notification_member_id (member_id, id),
    INDEX idx_notification_member_read (member_id, read_at)
) ENGINE = InnoDB;
