# 02. 금전·예약 동시성 검증

**목적:** 언어 전환 전후에 금액과 예약 상태가 같은 방식으로 확정되도록 실 MySQL 회귀 테스트를 보강한다. [상위 계획](00-kotlin-migration-plan.md)의 두 번째 P0 작업이다.

## 우선 확인할 코드

- [`CreditBalanceRepository`](../../backend/src/main/java/com/ovengers/slotkey/credit/repository/CreditBalanceRepository.java): `balance >= amount` 조건부 차감, 증가 쿼리.
- [`CreditServiceImpl`](../../backend/src/main/java/com/ovengers/slotkey/credit/service/CreditServiceImpl.java): 차감·환불·위약금과 거래 원장 저장.
- [`ReservationRepository`](../../backend/src/main/java/com/ovengers/slotkey/reservation/repository/ReservationRepository.java), [`ReservationSlotRepository`](../../backend/src/main/java/com/ovengers/slotkey/reservation/repository/ReservationSlotRepository.java): 상태 조건부 UPDATE, 슬롯 조회·삭제.
- [`ReservationCancelService`](../../backend/src/main/java/com/ovengers/slotkey/reservation/service/ReservationCancelService.java): 취소와 `REFUND`/`PENALTY` 기록을 한 트랜잭션에서 실행한다.
- 기존 테스트: [`ReservationPaymentConcurrencyTest`](../../backend/src/test/java/com/ovengers/slotkey/reservation/service/ReservationPaymentConcurrencyTest.java), [`ReservationCancelConcurrencyTest`](../../backend/src/test/java/com/ovengers/slotkey/reservation/service/ReservationCancelConcurrencyTest.java), [`CreditBalanceConcurrencyIntegrationTest`](../../backend/src/test/java/com/ovengers/slotkey/credit/CreditBalanceConcurrencyIntegrationTest.java). 이미 검증되는 시나리오는 중복 작성하지 않는다.

## 테스트 추가 순서

1. **동시 차감.** 서로 다른 예약 두 건이 동일 회원 잔액을 동시에 차감하고 총 요청액이 잔액을 초과하도록 준비한다. 각 스레드는 별도 트랜잭션·커넥션에서 서비스 경로를 호출하고 `CountDownLatch`로 시작을 맞춘다. 성공/실패 개수, 최종 잔액의 음수 방지, 성공 예약에 대응하는 거래만 생성됐는지를 DB에서 확인한다.
2. **원장 정합성.** 회원가입 지급, 관리자 지급, 예약 차감, 전액 환불과 위약금 환불을 한 흐름으로 실행한다. 각 거래의 `amount`, `type`, `balanceAfter`와 최종 `member.balance`를 검증한다. 합계 계산에는 테스트 시작 잔액 또는 초기 지급 행을 명시적으로 포함한다.
3. **취소 경계.** `Clock`을 고정해 시작 1시간 전, 그 직후, 시작 정각을 검증한다. 전액 환불이면 `REFUND` 한 건, 위약금 구간이면 `REFUND`와 `PENALTY` 각 한 건 및 실제 순환급액을 확인한다. 거절된 취소는 상태·슬롯·잔액·원장에 변화가 없어야 한다.
4. **예약 상태 전이.** `HELD` 만료 정각의 결제, 일반 취소의 중복 호출, 이미 체크인·노쇼·완료된 예약에 대한 취소/연장을 필요한 조합으로 검증한다. 조건부 UPDATE 영향 행이 0일 때 오류 코드와 DB 상태를 함께 확인한다. [미검증 목록](../../docs/test-strategy.md#9-현재-미구현-및-미검증-영역-gap-analysis)에 결과를 반영한다.
5. **동시성 재현성.** 테스트 메서드 전체에 `@Transactional`을 붙여 스레드가 부모 트랜잭션을 공유한다고 가정하지 않는다. 각 작업의 시작/종료를 동기화하고 `Future` 결과와 예외를 모두 수집한다. 최종 판정은 새 DB 조회로 수행한다. 고정 대기 시간에 의존하는 테스트는 최소화한다.

## 수정 판단과 완료 조건

현행 Java 코드에서 실패하면 Kotlin 변환과 분리해 벌크 UPDATE 뒤 영속성 컨텍스트 재조회, 환불·위약금의 트랜잭션 참여, 락 획득 순서, 영향 행 처리를 확인한다. 쿼리나 스키마를 바꾼다면 해당 통합 테스트와 Flyway 마이그레이션을 같은 변경에 포함한다. 언어 전환 PR에서 금전 규칙과 잠금 순서를 동시에 바꾸지 않는다.

- 신규 테스트는 `MySqlTestContainerConfig`의 MySQL 8.4에서 통과한다. H2 결과로 대체하지 않는다.
- `cd backend && ./gradlew test --tests 'com.ovengers.slotkey.reservation.service.*' --tests 'com.ovengers.slotkey.credit.*'`와 전체 `./gradlew build`를 실행한다.
- 성공 건수, 잔액·원장, 예약·슬롯 상태와 경계 시각을 실제 DB에서 확인하고 결과를 `docs/test-results.md`에 기록한다.

관련 결정: [예약 동시성](../../docs/decisions/reservation-concurrency.md), [예약 멱등성](../../docs/decisions/reservation-idempotency.md).
