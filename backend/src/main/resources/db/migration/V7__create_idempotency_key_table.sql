# 일시: 2026-09-15
# 담당: 이태호 (reservation.pay의 Idempotency-Key 저장/재생용 — global 인프라)
# 참고: docs/core-domain-decisions.md §2-1, api-명세서.md 1-7
# 비고: 같은 (idempotency_key, member_id, request_path) 조합의 재요청은
#       새로 처리하지 않고 저장된 최초 응답을 그대로 반환한다.

CREATE TABLE idempotency_key (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    idempotency_key VARCHAR(100) NOT NULL,
    member_id       BIGINT NOT NULL,
    request_path    VARCHAR(255) NOT NULL,
    response_status INT NOT NULL,
    response_body   TEXT NOT NULL,
    created_at      DATETIME NOT NULL,
    CONSTRAINT uq_idempotency_key_scope UNIQUE (idempotency_key, member_id, request_path),
    CONSTRAINT fk_idempotency_key_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE = InnoDB;
