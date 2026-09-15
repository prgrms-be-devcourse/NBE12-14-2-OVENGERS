# 일시: 2026-09-15
# 담당: 미정 (payment 도메인이었던 백한비님 영역 — payment 테이블 삭제(§1-1, §11)에 따른 대체 테이블)
# 테이블: credit_transaction
# 선행 의존(FK 대상): member, reservation
# 참고: docs/core-domain-decisions.md §1-4, §11
# 비고: V1(member)이 실제 테이블을 갖기 전에는 이 마이그레이션이 FK 오류로 실패한다.
#       Flyway는 버전 순서(V1 → V3 → V6)대로 적용되므로, V1이 먼저 채워지기만 하면 순서 자체는 문제없다.

CREATE TABLE credit_transaction (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id      BIGINT NOT NULL,
    amount         INT NOT NULL,           -- 부호 있음: 지급/환급 +, 차감/위약금 - (SUM(amount) = 잔액)
    type           VARCHAR(30) NOT NULL,   -- SIGNUP_GRANT, ADMIN_GRANT, RESERVATION_CHARGE, REFUND, PENALTY
    reservation_id BIGINT NULL,            -- 지급 건(SIGNUP_GRANT, ADMIN_GRANT)은 예약과 무관하므로 NULL 허용
    balance_after  INT NOT NULL,
    reason         VARCHAR(500) NULL,      -- ADMIN_GRANT만 필수 (애플리케이션 레벨에서 검증)
    created_at     DATETIME NOT NULL,
    CONSTRAINT fk_credit_transaction_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_credit_transaction_reservation FOREIGN KEY (reservation_id) REFERENCES reservation (id)
) ENGINE = InnoDB;

CREATE INDEX idx_credit_transaction_member_created ON credit_transaction (member_id, created_at);
