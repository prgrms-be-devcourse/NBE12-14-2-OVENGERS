# 김재철님
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

    PRIMARY KEY (id)
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