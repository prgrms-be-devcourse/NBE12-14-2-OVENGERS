-- 일시: 2026-10-16
-- 기능: 베스트 공간 랭킹 2단계(확정 스냅샷 테이블, 집계용 인덱스)
-- 참고: docs/decisions/space-ranking.md, docs/plans/space-ranking/00-space-ranking-plan.md §2
--
-- 끝난 기간(DAILY/WEEKLY/MONTHLY)을 00:01(KST)에 한 번 계산해 저장하는 확정값이다.
-- period_start는 기간의 첫 날(주간은 월요일, 월간은 1일). 순위(rank)는 저장하지 않고 조회 시 계산한다.
-- UNIQUE(period_type, period_start, space_id)로 같은 기간을 다시 계산해도 덮어쓰기(멱등)가 된다.
-- location은 계산 당시 값을 저장해 공간 지역이 바뀌어도 과거 지역 랭킹이 바뀌지 않게 한다.
-- net_revenue는 환불이 몰리면 음수가 될 수 있어 부호 있는 BIGINT이다.
-- FK를 걸지 않는다(독립 집계 테이블, space/reservation 잠금과 엮이지 않게 하기 위함).

CREATE TABLE space_ranking_snapshot (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    period_type       VARCHAR(10) NOT NULL,   -- DAILY / WEEKLY / MONTHLY
    period_start      DATE NOT NULL,
    space_id          BIGINT NOT NULL,
    location          VARCHAR(255) NOT NULL,
    net_revenue       BIGINT NOT NULL,
    reservation_count INT NOT NULL,
    slot_count        INT NOT NULL,
    computed_at       DATETIME NOT NULL,
    CONSTRAINT uk_space_ranking_snapshot_period_space UNIQUE (period_type, period_start, space_id)
) ENGINE = InnoDB;

-- 예약 건수·슬롯 수 집계: status IN (...) AND start_time 범위 → space_id별 그룹
CREATE INDEX idx_reservation_status_start_space ON reservation (status, start_time, space_id);

-- 순매출 집계: type IN (...) AND created_at 범위 → reservation_id로 예약 조인
CREATE INDEX idx_credit_transaction_type_created_reservation ON credit_transaction (type, created_at, reservation_id);
