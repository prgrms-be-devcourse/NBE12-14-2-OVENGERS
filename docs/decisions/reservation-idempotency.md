# 기술적 의사결정 — 예약 요청 멱등성

> **2026-09-15 갱신**: `docs/core-domain-decisions.md` §2-1 반영 — `Idempotency-Key`가 붙는 지점이 "예약 생성"이 아니라 "결제 확정"으로 정정되었고, `payment`가 삭제되어 `credit_transaction`으로 대체됨.

## 문제 상황

예약 확정은 크레딧 차감(Mock 결제)을 동반한다. 네트워크 재시도나 사용자의 중복 클릭으로 동일한 결제 확정 요청이 여러 번 전송되면, 크레딧 차감과 예약 확정이 중복 처리될 위험이 있다.

## 요구 조건

- 동일한 결제 확정 요청을 같은 Idempotency-Key로 3회 중복 제출해도 크레딧 차감·예약 확정은 한 번만 처리된다(DoD).
- 서로 다른 키로 같은 시간을 요청하는 것은 멱등성이 아니라 예약 동시성 규칙(`reservation-concurrency.md`)이 처리한다 — 두 개념을 혼동하지 않는다.
- 같은 키로 다른 본문을 보냈을 때의 파라미터 충돌 검사 정책은 현재 구현되어 있지 않으며 향후 과제로 남겨둔다.

## 선택 및 근거

- **돈이 실제로 움직이는 지점**, 즉 `POST /reservations/{id}/pay`가 `Idempotency-Key`(UUID) 헤더를 필수로 받는다(API 명세서 §1, §5-2). `POST /reservations`(HOLD 생성)에는 결제가 없으므로 대상이 아니다. *(2026-09-15 정정 — 기존에는 "예약 생성" POST에 붙어 있었음)*
- **성공 응답만 저장 및 재생**:
  - `ReservationPaymentConfirmService`는 결제 및 조건부 상태 전이가 성공(200 OK)한 경우에만 `idempotency_key` 테이블에 응답을 저장한다.
  - 결제 실패(잔액 부족 `INSUFFICIENT_BALANCE` 422, 버전 불일치 409 등) 시에는 예외 발생으로 트랜잭션 전체가 롤백되어 멱등성 레코드가 생성되지 않는다.
  - 따라서 실패한 요청은 Idempotency-Key를 소진하지 않으며, 사용자가 관리자 지급 등으로 잔액이 늘어난 뒤 HOLD 만료 전 동일한 키로 재결제를 시도하면 정상 처리된다.
- 키의 적용 범위(Scope)는 `(idempotency_key, member_id, request_path)` 유니크 인덱스로 관리되며, 요청 본문이나 파라미터는 저장·매핑되지 않는다. 결제 성공 응답 저장은 결제 트랜잭션 커밋과 함께 수행된다.

## 검증 방법

- 같은 Idempotency-Key로 동일한 결제 확정 요청을 3회 연속 순차 전송 시 매번 동일한 CONFIRMED 응답(상태 CONFIRMED 및 예약 ID 일치)을 돌려받고, `credit_transaction` 1건 기록, 잔액 1회 차감, `idempotency_key` 1건 저장을 확인 (순차 재요청 DB 통합 테스트 `ReservationPaymentConcurrencyTest#pay_sameKeyRepeated_chargesOnce`, 단위 테스트 `ReservationPaymentConfirmServiceTest`는 캐시 응답 반환 및 저장 호출 Mock 검증).
- 결제 실패(잔액 부족) 후 관리자 지급 등으로 잔액이 늘어난 뒤 HOLD 만료 전 같은 Idempotency-Key로 재시도 시 성공하는지 확인.
- 다중 스레드 동시 결제 경합 DB 통합 테스트(`ReservationPaymentConcurrencyTest#pay_sameKeyConcurrently_chargesOnceAndReplays`, `pay_differentKeysConcurrently_chargesOnce`)로 동일 키 동시 경합(1건 이상 성공, 밀린 요청 409 후 재요청 시 최초 200 응답 재생, 차감 1회) 및 상이 키 동시 경합(1건 성공, 나머지 409, 차감 1회) 시 원자적 차감 및 멱등 응답 확인.

## 남은 과제 및 한계

- **보관 기간(TTL) 부재 (별도 과제)**: `idempotency_key` 테이블(V7)에는 만료 일시 컬럼이나 자동 삭제 스케줄러가 없어 현재 레코드가 계속 누적된다. 운영 시점의 만료 기간 정책 수립과 삭제 배치가 필요하다.
- **동시 진입 경합(Race Condition)의 트레이드오프**:
  - 캐시 조회(`find`)와 저장(`save`)이 분리되어 있어, 동일한 키의 요청이 완전히 동시에 도착하면 둘 다 캐시 미스가 발생하여 결제 로직으로 함께 진입할 수 있다.
  - 이 경우에도 DB의 조건부 UPDATE(`confirmIfHeldAndNotExpired`)가 동작하여 단 1건만 최종 확정(200 OK)되고 다른 요청은 409(`RESERVATION_STATE_CONFLICT`)를 받으므로 이중 결제는 원천 방어된다.
  - 409를 받은 클라이언트가 동일 키로 재시도하면 그때는 먼저 저장된 200 OK 성공 응답을 그대로 돌려받는다(`IdempotencyService` 주석 참고, `ReservationPaymentConcurrencyTest`로 검증).