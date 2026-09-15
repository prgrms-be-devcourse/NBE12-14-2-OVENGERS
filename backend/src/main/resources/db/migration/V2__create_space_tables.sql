# 김재철님
# 2026-09-15 수정(이태호, core-domain-decisions.md §11 반영):
#   - 테이블명을 `spaces`로 통일하고 V3의 FK 참조도 함께 수정
#   - version 컬럼 추가 (INT, 낙관적 잠금/비교용, §5-2)
#   - CHECK(opening_time < closing_time) 추가 (§11)
CREATE TABLE spaces (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    location VARCHAR(255) NOT NULL,
    description TEXT,
    capacity INT NOT NULL,
    price_per_slot BIGINT NOT NULL,
    image_path VARCHAR(500),
    opening_time TIME NOT NULL,
    closing_time TIME NOT NULL,
    status VARCHAR(20) NOT NULL,
    version INT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    CONSTRAINT chk_space_operating_hours CHECK (opening_time < closing_time)
);

CREATE TABLE audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_member_id BIGINT,
    action VARCHAR(30) NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id BIGINT NOT NULL,
    reason VARCHAR(500),
    before_value TEXT,
    after_value TEXT,
    created_at DATETIME NOT NULL,

    PRIMARY KEY (id)
);

# audit_logs 테이블에 특정 대상 조회를 위한 복합 인덱스.
CREATE INDEX idx_audit_logs_target ON audit_logs (target_type, target_id);
