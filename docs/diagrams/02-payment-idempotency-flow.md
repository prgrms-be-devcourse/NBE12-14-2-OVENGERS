# 멱등성 흐름 — 같은 Idempotency-Key는 최초 성공 응답을 재생한다

> **한 줄 메시지**: 결제 확정(`POST /reservations/{id}/pay`)이 성공하면 응답을 같은 트랜잭션에 저장하고, 같은 키로 다시 오면 결제 로직을 건너뛰고 그 응답을 돌려준다. 실패한 요청은 키를 소진하지 않는다.

```mermaid
sequenceDiagram
    autonumber
    participant C as 클라이언트
    participant S as ReservationPaymentConfirmService
    participant DB as MySQL

    C->>S: POST /reservations/10/pay<br/>Idempotency-Key: K1

    alt ① 같은 키로 이미 성공한 적 있음 (재시도·중복 클릭)
        S->>DB: idempotency_key 조회 (key, member, path)
        DB-->>S: 저장된 응답 있음
        S-->>C: 200 CONFIRMED (저장된 응답 그대로)
        Note over S,DB: 크레딧 차감·상태 전이 없음
    else ② 최초 요청
        S->>DB: idempotency_key 조회
        DB-->>S: 없음
        S->>DB: 소유권 확인, Space 공유 락, version 비교
        S->>DB: 크레딧 조건부 차감
        S->>DB: UPDATE reservation SET CONFIRMED<br/>WHERE status='HELD' AND now < hold_expires_at
        alt 영향 행 1
            S->>DB: 상태 이력 + idempotency_key(응답 JSON) INSERT
            S->>DB: COMMIT (차감, 확정, 키 저장이 한 번에)
            S-->>C: 200 CONFIRMED
        else 잔액 부족 / version 불일치 / 영향 행 0
            S->>DB: ROLLBACK (키도 저장되지 않음)
            S-->>C: 422 INSUFFICIENT_BALANCE / 409
            Note over C,DB: 키가 소진되지 않아<br/>잔액 충전 후 같은 키로 재시도 가능
        end
    end
```

## 동시에 같은 키가 도착하는 좁은 경합 (의도적으로 남긴 틈)

```mermaid
sequenceDiagram
    autonumber
    participant A as 요청 A (키 K1)
    participant B as 요청 B (키 K1)
    participant DB as MySQL

    A->>DB: 키 조회 → 없음
    B->>DB: 키 조회 → 없음 (A가 아직 저장 전)
    A->>DB: 크레딧 차감 + 조건부 UPDATE (HELD→CONFIRMED) 성공
    B->>DB: 조건부 UPDATE → 영향 행 0 (이미 CONFIRMED)
    DB-->>B: B 전체 롤백 (차감 취소)
    A->>DB: 키 저장 + COMMIT
    A-->>A: 200 CONFIRMED
    B-->>B: 409 RESERVATION_STATE_CONFLICT
    B->>DB: 같은 키 K1로 재시도 → 키 조회 → 있음
    DB-->>B: 200 CONFIRMED 재생 (차감은 1회뿐)
```

## 왜 이렇게 했는가

- **돈이 움직이는 `/pay`에만 키를 요구한다.** HOLD 생성(`POST /reservations`)은 결제가 없어서 대상이 아니다.
- **성공 응답만 저장한다.** 실패는 롤백되어 키가 남지 않으므로, 잔액을 채운 뒤 HOLD 만료 전에 같은 키로 다시 결제할 수 있다.
- **키 저장을 결제 트랜잭션에 넣었다.** 차감과 확정은 됐는데 키 저장만 빠지는 중간 상태가 생기지 않는다.
- **이중 결제의 최종 방어선은 키가 아니라 조건부 UPDATE다.** 조회와 저장이 분리되어 동시 경합 창이 남지만, 한쪽만 확정되고 다른 쪽은 409로 롤백된다. 이 틈을 막으려고 락을 더 얹지 않은 것이 트레이드오프다.

## 한계

- 같은 키에 **다른 본문**을 보냈을 때의 충돌 검사는 없다(키 범위는 `idempotency_key + member_id + request_path`).
- `idempotency_key` 테이블에 보관 기간(TTL)이 없어 레코드가 계속 쌓인다.

## 근거

| 내용 | 위치 |
| --- | --- |
| 처리 순서, 롤백 조건 | `backend/src/main/java/com/ovengers/slotkey/reservation/service/ReservationPaymentConfirmService.java` `confirm` |
| 키 조회·저장, 경합 창 주석 | `backend/src/main/java/com/ovengers/slotkey/global/idempotency/IdempotencyService.java` |
| 유니크 제약 | `backend/src/main/resources/db/migration/V7__create_idempotency_key_table.sql` `uq_idempotency_key_scope` |
| 검증 테스트(문서 기술 기준, 테스트 파일은 직접 열어보지 않음) | `ReservationPaymentConcurrencyTest#pay_sameKeyRepeated_chargesOnce`, `#pay_sameKeyConcurrently_chargesOnceAndReplays` |
| 결정 기록 | [`docs/decisions/reservation-idempotency.md`](../decisions/reservation-idempotency.md), [`docs/decisions/reservation-concurrency.md`](../decisions/reservation-concurrency.md) |
