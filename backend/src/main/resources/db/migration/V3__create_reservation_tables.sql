# 일시: 2026-09-14
# 담당: 이태호 (reservation, reservation_slot)
# 테이블: reservation, reservation_slot, reservation_status_history
# 선행 의존(FK 대상): member, space
# 참고: docs/erd.md, docs/decisions/reservation-concurrency.md, docs/core-domain-decisions.md
# 비고: reservation_status_history는 백한비님 대신 이태호가 작성
#
# 2026-09-15 수정(core-domain-decisions.md §11 반영):
#   - completed_at 컬럼 제거 → checked_out_at으로 통합 (체크아웃 시각 = 완료 시각)
#   - hold_expires_at, checked_in_at, checked_out_at 컬럼 추가
#   - status 값 3개(CONFIRMED/CANCELLED/COMPLETED) → 7개(HELD/EXPIRED/CONFIRMED/IN_USE/COMPLETED/CANCELLED/NO_SHOW, §3-2)
#     컬럼 타입(VARCHAR(20))은 그대로 유지, 허용 값만 애플리케이션 enum에서 확장됨
#   - CHECK 제약 5종 추가 (start_time/end_time 및 상태별 시각 필수 조건)

CREATE TABLE reservation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id                BIGINT NOT NULL,
    space_id                 BIGINT NOT NULL,
    start_time               DATETIME NOT NULL,
    end_time                 DATETIME NOT NULL,
    status                   VARCHAR(20) NOT NULL,   -- HELD/EXPIRED/CONFIRMED/IN_USE/COMPLETED/CANCELLED/NO_SHOW
    price_per_slot_snapshot  INT NOT NULL,
    total_amount             INT NOT NULL,
    hold_expires_at          DATETIME NULL,           -- HELD 홀드 만료 시각 (§2-1)
    checked_in_at            DATETIME NULL,           -- 최초 체크인 시각 (§8-2)
    checked_out_at           DATETIME NULL,           -- 체크아웃/자동퇴실 시각. 자동퇴실 시 end_time을 넣음 (§3-4)
    cancelled_at             DATETIME NULL,
    created_at               DATETIME NOT NULL,
    CONSTRAINT fk_reservation_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_reservation_space FOREIGN KEY (space_id) REFERENCES space (id),
    CONSTRAINT chk_reservation_time_range CHECK (start_time < end_time),
    CONSTRAINT chk_reservation_held_hold_expires CHECK (status <> 'HELD' OR hold_expires_at IS NOT NULL),
    CONSTRAINT chk_reservation_cancelled_at CHECK (status <> 'CANCELLED' OR cancelled_at IS NOT NULL),
    CONSTRAINT chk_reservation_completed_checked_out CHECK (status <> 'COMPLETED' OR checked_out_at IS NOT NULL),
    CONSTRAINT chk_reservation_checked_in CHECK (status NOT IN ('IN_USE', 'COMPLETED') OR checked_in_at IS NOT NULL)
) ENGINE = InnoDB;


CREATE TABLE reservation_slot (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_id BIGINT NOT NULL,
    space_id       BIGINT NOT NULL,
    slot_start     DATETIME NOT NULL,
    CONSTRAINT fk_reservation_slot_reservation FOREIGN KEY (reservation_id) REFERENCES reservation (id),
    CONSTRAINT fk_reservation_slot_space FOREIGN KEY (space_id) REFERENCES space (id),
    CONSTRAINT uq_reservation_slot_space_start UNIQUE (space_id, slot_start)
) ENGINE = InnoDB;


CREATE TABLE reservation_status_history (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_id       BIGINT NOT NULL,
    changed_by_member_id BIGINT NULL,
    from_status          VARCHAR(20) NULL,
    to_status            VARCHAR(20) NOT NULL,
    reason               VARCHAR(255) NULL,
    changed_at           DATETIME NOT NULL,
    CONSTRAINT fk_reservation_status_history_reservation FOREIGN KEY (reservation_id) REFERENCES reservation (id),
    CONSTRAINT fk_reservation_status_history_member FOREIGN KEY (changed_by_member_id) REFERENCES member (id)
) ENGINE = InnoDB;
