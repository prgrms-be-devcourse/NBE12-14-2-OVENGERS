-- 일시: 2026-10-16
-- 기능: 예약 대기열 4단계(컨슈머 멱등 처리)
-- 참고: docs/decisions/outbox-kafka.md "컨슈머 결정", docs/plans/waitlist-outbox/02-outbox-kafka-design.md §2
--
-- 컨슈머가 이미 처리한 이벤트를 기록한다. 처리 시작 시 같은 트랜잭션에서 INSERT하고,
-- UNIQUE(consumer, event_id) 때문에 같은 컨슈머가 같은 이벤트를 두 번 넣을 수 없다.
-- consumer 칸이 있는 이유는 같은 토픽을 다른 컨슈머(예: 감사)가 읽어도 서로의 처리 이력에 막히지 않게 하기 위함이다.
-- FK를 걸지 않는다(outbox_event와 같은 이유).

CREATE TABLE processed_event (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    consumer     VARCHAR(50) NOT NULL,
    event_id     VARCHAR(36) NOT NULL,
    processed_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_processed_event_consumer_event UNIQUE (consumer, event_id)
) ENGINE = InnoDB;
