-- 일시: 2026-10-15
-- 기능: 예약 대기열 1단계(대기 신청·취소·내 목록)
-- 참고: docs/plans/waitlist-outbox/01-waitlist-rules.md
--
-- FK를 걸지 않는다(member/space 잠금 순서 데드락 이력, 01 문서 "데이터와 API").
--
-- 중복 신청은 UNIQUE(member_id, space_id, start_time, end_time, active)가 DB 레벨에서 막는다.
-- active는 WAITING일 때만 1이고 그 밖의 상태에서는 NULL이다. MySQL의 UNIQUE는 NULL을 서로
-- 다른 값으로 보므로, "활성 신청은 구간당 1건"을 강제하면서 취소된 이력 행은 여러 개 남길 수 있다
-- (door_access_token의 active_reservation_id + UNIQUE와 같은 방식). 취소 시 행을 지우지 않는 이유는
-- 누가 언제 대기를 걸었다 취소했는지가 알림 대상 선정 논의의 근거로 남아야 하기 때문이다.
--
-- 활성(WAITING) 3건 제한은 애플리케이션 검사이며 동시 요청 시 초과를 수용한다.

CREATE TABLE waitlist_entry (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id        BIGINT NOT NULL,
    space_id         BIGINT NOT NULL,
    start_time       DATETIME NOT NULL,
    end_time         DATETIME NOT NULL,
    status           VARCHAR(20) NOT NULL,   -- WAITING/CANCELLED/EXPIRED
    active           TINYINT NULL,           -- WAITING이면 1, 그 밖에는 NULL (UNIQUE 부분 적용용)
    last_notified_at DATETIME NULL,          -- 4단계(알림) 에서 사용
    created_at       DATETIME NOT NULL,
    CONSTRAINT uk_waitlist_entry_member_space_range_active
        UNIQUE (member_id, space_id, start_time, end_time, active),
    -- NULL 안전 비교(<=>)를 쓰는 이유: CHECK는 결과가 FALSE일 때만 막고 NULL(unknown)이면 통과시킨다.
    -- `active = 1`로 쓰면 (status='WAITING', active=NULL) 행에서 식이 unknown이 되어 그대로 들어간다.
    CONSTRAINT chk_waitlist_entry_active CHECK (
        (status = 'WAITING' AND active <=> 1) OR (status <> 'WAITING' AND active IS NULL)),
    CONSTRAINT chk_waitlist_entry_time_range CHECK (start_time < end_time),
    INDEX idx_waitlist_entry_space_status_start (space_id, status, start_time),
    INDEX idx_waitlist_entry_member_status (member_id, status)
) ENGINE = InnoDB;

-- 대기 신청 시 "같은 회원의 겹치는 HELD/CONFIRMED/IN_USE 예약" 검사가 쓰는 인덱스.
-- 기존 인덱스는 start_time, (space_id, start_time)뿐이어서 member_id 조건이 선행하는
-- 이 검사가 range 스캔으로 떨어진다(01 문서 "인덱스" 항목).
CREATE INDEX idx_reservation_member_space_status_start
    ON reservation (member_id, space_id, status, start_time);
