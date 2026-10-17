-- 일시: 2026-10-17
-- 기능: PG 충전 5단계(웹훅 수신 기록)
-- 참고: docs/decisions/pg-charge.md "5단계 설계 결정" 결정 2·3
--
-- 토스 PAYMENT_STATUS_CHANGED 웹훅을 받은 즉시 한 행으로 기록하고 200을 준다. 처리(PG 조회 후 확정)는 따로 한다.
-- UNIQUE(transmission_id) + INSERT IGNORE로 같은 알림의 중복 수신은 한 행만 남긴다.
-- order_id, payment_key, reported_status는 서명 없는 본문에서 꺼낸 힌트이며 충전 확정의 근거로 쓰지 않는다.
-- 원본 본문은 저장하지 않는다(카드·구매자 정보 최소화).
-- 한 주문에 알림이 여러 번 올 수 있고 주문 삭제와 생명주기가 다르므로 charge_order에 FK를 걸지 않는다.
-- status: RECEIVED(수신) -> DONE(처리기 처리 완료). result는 처리 결과 이름(CONFIRMED, ALREADY_PAID, ORDER_PENDING 등,
-- pg-charge.md 5단계 결정 4-2·4-3)이며 처리 전에는 NULL이다.

CREATE TABLE pg_webhook_event (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    transmission_id VARCHAR(100) NOT NULL,
    event_type      VARCHAR(50)  NOT NULL,
    order_id        VARCHAR(64)  NOT NULL,
    payment_key     VARCHAR(200) NULL,
    reported_status VARCHAR(30)  NULL,
    retried_count   INT          NULL,
    status          VARCHAR(20)  NOT NULL,
    result          VARCHAR(40)  NULL,
    received_at     DATETIME(6)  NOT NULL,
    processed_at    DATETIME(6)  NULL,
    CONSTRAINT uk_pg_webhook_event_transmission UNIQUE (transmission_id),
    INDEX idx_pg_webhook_event_status_id (status, id)
) ENGINE = InnoDB;
