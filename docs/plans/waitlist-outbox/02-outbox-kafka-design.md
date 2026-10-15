# Outbox·Kafka·알림 컨슈머 설계 (10/21)

> 작성: 2026-10-15. [계획](00-waitlist-outbox-plan.md)의 2~4단계 범위다. 대기자 선정에 쓰는 신청 규칙은 [01 대기 신청 규칙](01-waitlist-rules.md)을 따른다.

## 1. 이벤트·Outbox·컨슈머

- 이벤트 `SlotReleased`, 토픽 `slot-released`, 파티션 키 `spaceId`. 발행 지점은 취소(`ReservationCancelService.cancel`), 강제 취소(`AdminReservationService.forceCancel`), 홀드 만료(`ReservationHoldExpirationService.expireHold`, `updated == 1`일 때만) 3곳이며 `reason`(CANCELLED / FORCE_CANCELLED / HOLD_EXPIRED)으로 구분한다. 페이로드는 `eventId`, `reservationId`, `reason`, `occurredAt`이다.
- outbox INSERT는 각 트랜잭션의 **마지막 쓰기**로 둔다. 롤백되면 이벤트도 없다. `expireHold`는 다른 사용자의 슬롯 확보 트랜잭션 안에서 호출되어 풀린 슬롯이 곧바로 재점유될 수 있으므로, 컨슈머가 "지금 비었는가"를 다시 확인한다.
- Relay: `@Scheduled` 폴링. PENDING을 id 순으로 N건 읽어 건별 트랜잭션으로 Kafka에 동기 전송하고 성공하면 PUBLISHED, 실패하면 `attempts`를 올리고 **다음 건으로 계속**한다(컨슈머가 순서에 의존하지 않고 DB 상태를 다시 읽는다). at-least-once, 인스턴스 1개 가정.
- Kafka 접근은 포트(`OutboxPublisher`)로 감싼다(Kafka 구현 + 테스트용 Fake). `slotkey.kafka.enabled=false`면 relay와 리스너가 뜨지 않는다.
- 컨슈머: 리스너는 얇게, 로직은 `SlotReleasedHandler.handle(eventId, reservationId)`에 둔다. 한 트랜잭션에서 `processed_event(consumer, event_id)` UNIQUE로 멱등을 확보하고 알림을 저장한다.
- 선행 조건(1단계에서 확인됨, step-1-review.md M3): `WaitlistEntry`에 `@DynamicUpdate`/`@Version`이 없어 **전체 컬럼 UPDATE**가 나간다. 컨슈머가 `last_notified_at`을 쓰기 시작하면, 먼저 로드해 둔 `cancel()`의 flush가 그 값을 읽은 시점의 값(null)으로 **되돌릴 수 있다**(lost update → 중복 알림). 둘 중 하나로 막는다: ① `WaitlistEntry`에 `@DynamicUpdate`를 붙인다 ② `last_notified_at`을 엔터티 더티체킹이 아니라 조건부 UPDATE로 따로 쓴다(`UPDATE waitlist_entry SET last_notified_at=:now WHERE id=:id AND status='WAITING'`). 또한 `WAITING → EXPIRED` 전이를 넣을 때는 `active`도 NULL로 함께 비워야 한다(`chk_waitlist_entry_active`).
- 알림 대상: 같은 space, 활성 WAITING(`start_time > now` — 1단계와 같은 기준), 해제 구간과 겹침, **신청 구간 전체의 점유 슬롯 0건**, 회원 ACTIVE, **본인의 만료되지 않은 `HELD`·`CONFIRMED`·`IN_USE` 예약이 겹치지 않음**(1단계가 만든 `ReservationRepository.existsOccupyingByMemberAndSpace`를 그대로 호출한다. `COMPLETED`는 제외 — 01 문서 §1 "대상 상태"). 해당자 전원에게 알림을 저장한다. 알림 후에도 항목은 WAITING을 유지하고 `last_notified_at`만 갱신한다. 정지·탈퇴 회원은 알림만 건너뛰고 항목은 유지한다.
- 테스트: 포트 + Fake, 핸들러 직접 호출로 CI를 구성한다(AGENTS.md "CI는 Fake만"). 실제 Kafka는 로컬 compose에서 수동 E2E 1회 후 notes에 결과를 남긴다.

## 2. 데이터

모두 FK를 걸지 않는다.

| 테이블 | 컬럼 | 제약·인덱스 |
| --- | --- | --- |
| `outbox_event` | id, event_id(UUID), event_type, topic, partition_key, payload(JSON), status(PENDING/PUBLISHED), attempts, created_at, published_at | UNIQUE(event_id), INDEX(status, id) |
| `processed_event` | id, consumer, event_id, processed_at | UNIQUE(consumer, event_id) |
