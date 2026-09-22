# 기술적 의사결정 — 예약 동시성 제어

> **2026-09-21 갱신**: `03d2d5d6`의 결제·연장 동시성 처리와 시간 정책을 반영했다. 이 변경은 선행 PR의 Space 공유·배타 락(`findByIdForShare`, `findByIdForUpdate`)이 적용되어 있다는 전제에서 동작한다.

## 문서 목적

이 문서는 다음 질문에 답한다.

- 결제와 연장이 왜 `Space`를 먼저 잠그는가?
- 결제는 왜 소유권을 잠금과 버전 검사보다 먼저 확인하는가?
- 연장은 왜 `Reservation`까지 배타 잠금하는가?
- 비관적 락, 조건부 UPDATE, 슬롯 UNIQUE 제약은 각각 무엇을 보호하는가?

핵심 규칙은 하나다.

> 두 리소스를 모두 다루는 트랜잭션은 항상 `Space → Reservation` 순서로 접근한다. 다만 결제는 Reservation 배타 락 대신 조건부 UPDATE를 최종 문지기로 사용한다.

## 먼저 구분할 것: 점유와 DB 행 잠금

이 문서에서 말하는 두 종류의 “잠금”은 목적과 수명이 다르다.

| 구분 | 수단 | 유지 시간 | 목적 |
| --- | --- | --- | --- |
| 예약 슬롯 점유 | `reservation_slot` 행과 `UNIQUE(space_id, slot_start)` | HOLD 만료 또는 예약 종료·취소까지 | 같은 시간대를 두 예약이 차지하지 못하게 함 |
| DB 행 잠금 | `PESSIMISTIC_READ`/`PESSIMISTIC_WRITE` | 현재 DB 트랜잭션이 끝날 때까지 | Space 수정과 예약 처리의 실행 순서를 정하고 최신 상태를 읽게 함 |

사용자가 결제 화면에 머무는 10분 동안 DB 행 잠금을 유지하지 않는다. 이 구간의 점유는 `HELD` 예약과 슬롯 행으로 표현한다. DB 행 잠금은 실제 결제·연장 요청을 처리하는 짧은 트랜잭션 안에서만 사용한다.

## 해결하려는 경합

### Space 수정 ↔ 결제·연장

관리자가 가격이나 운영시간을 바꾸는 동안 결제 또는 연장이 동시에 실행되면, 한 요청 안에서 변경 전·후의 Space 정보가 섞일 수 있다.

- Space 수정은 선행 PR에서 Space **배타 락**을 사용한다.
- 결제와 연장은 Space **공유 락**을 사용한다.
- 공유 락끼리는 함께 실행될 수 있지만, 공유 락과 배타 락은 서로 기다린다.

따라서 먼저 잠금을 얻은 트랜잭션이 끝난 뒤 다음 트랜잭션이 최신 Space 상태를 확인한다.

### 같은 Reservation을 바꾸는 요청끼리의 경합

같은 예약에 연장 요청이 두 번 들어오거나 연장과 취소·체크아웃이 겹치면, 양쪽 요청이 모두 오래된 종료 시각과 상태를 믿어서는 안 된다.

- 연장은 Reservation **배타 락**으로 같은 예약에 대한 연장 처리를 직렬화한다.
- 마지막 UPDATE에도 `expectedEndTime`과 허용 상태를 조건으로 넣어 다시 검증한다.
- 결제는 `HELD`이면서 아직 만료되지 않은 경우에만 성공하는 조건부 UPDATE를 사용한다.

## 공통 잠금 순서: Space → Reservation

여러 행을 서로 다른 순서로 잠그면 데드락 가능성이 커진다. 예를 들어 트랜잭션 A가 Reservation을 잡고 Space를 기다리는데, 트랜잭션 B가 Space를 잡고 Reservation을 기다리면 둘 다 진행할 수 없다.

이 프로젝트는 다음 순서로 통일한다.

```text
reservationId로 spaceId 확인
        ↓
Space 행 잠금
        ↓
Reservation 최신 상태 확인 또는 행 잠금
        ↓
비즈니스 검증과 상태 변경
```

처음의 `spaceId` 투영 조회는 최종 상태를 판단하기 위한 조회가 아니다. 상위 리소스인 Space를 먼저 잠그기 위해 대상만 알아내는 단계다. 실제 변경 가능 여부는 필요한 잠금을 얻은 뒤 다시 읽은 상태와 조건부 UPDATE로 결정한다.

새로운 예약 변경 기능을 추가할 때도 Reservation을 먼저 잠근 뒤 Space를 잠그는 역순 경로를 만들지 않는다.

## 결제 확인 흐름

대상: `ReservationPaymentConfirmService.confirm`

```text
1. Idempotency-Key 검증 및 저장된 응답 조회
2. findTargetInfoById(reservationId)로 spaceId, memberId만 조회
3. 요청 회원과 예약 소유자가 같은지 확인
4. findByIdForShare(spaceId)로 Space 공유 락 획득
5. 요청의 spaceVersion과 현재 Space version 비교
6. Reservation 일반 조회
7. 크레딧 조건부 차감
8. HELD이고 now < holdExpiresAt일 때만 CONFIRMED로 조건부 UPDATE
9. 상태 이력과 멱등 응답 저장 후 커밋
```

### 소유권을 가장 먼저 확인하는 이유

비소유자는 Space 잠금을 획득할 필요가 없고 Space version이 맞는지 알 필요도 없다. 그래서 `spaceId`와 `memberId`만 투영 조회한 직후 소유권을 검사한다.

이 순서로 다음을 보장한다.

- 올바르거나 잘못된 `spaceVersion`을 보내도 비소유자에게는 항상 `FORBIDDEN_NOT_OWNER`가 먼저 반환된다.
- 비소유 요청이 Space 행 잠금을 잡아 정상 요청이나 관리자 수정을 불필요하게 기다리게 하지 않는다.
- 소유권 검사에 실패하면 크레딧 차감도 시도하지 않는다.

### Space 공유 락과 version 검증을 함께 쓰는 이유

`space.version`은 사용자가 결제 화면에서 확인한 가격 정책이 아직 최신인지 판단한다. 하지만 version을 읽는 순간 관리자가 Space를 수정할 수 있다면, 검사 직후 값이 바뀌는 문제가 남는다.

공유 락을 얻은 상태에서 version을 검사하면 결제 트랜잭션이 끝날 때까지 관리자의 Space 배타 수정이 기다린다.

```text
결제가 Space 공유 락을 먼저 획득
  → 결제는 같은 version으로 끝까지 처리
  → 관리자 수정은 결제 커밋 뒤 실행

관리자 수정이 Space 배타 락을 먼저 획득
  → 결제가 대기
  → 수정 커밋 뒤 최신 version을 읽음
  → 기존 version 요청이면 SPACE_VERSION_MISMATCH
```

### 결제가 Reservation 배타 락을 사용하지 않는 이유

결제의 최종 성공 조건은 한 번의 조건부 UPDATE에 들어 있다.

```sql
UPDATE reservation
SET status = 'CONFIRMED'
WHERE id = :id
  AND status = 'HELD'
  AND :now < hold_expires_at
```

영향받은 행이 1개면 성공이고 0개면 만료되었거나 이미 다른 요청이 처리한 것이다. 0개일 때 예외를 던지므로 앞서 수행한 크레딧 차감도 같은 트랜잭션에서 롤백된다. 따라서 결제는 Reservation 비관적 락 대신 조건부 UPDATE를 최종 문지기로 사용한다.

## 예약 연장 흐름

대상: `ReservationExtendService.extend`

```text
1. findSpaceIdById(reservationId)로 spaceId만 조회
2. findByIdForShare(spaceId)로 Space 공유 락 획득
3. findByIdForUpdate(reservationId)로 Reservation 배타 락 획득
4. 소유권, 상태(CONFIRMED/IN_USE), 종료 전 여부, expectedEndTime 검증
5. 새 종료 시각 정책 검증
6. 추가 슬롯 INSERT — UNIQUE 충돌이면 전체 롤백
7. 원 예약의 pricePerSlotSnapshot으로 추가 금액 계산 및 차감
8. expectedEndTime과 상태를 조건으로 종료 시각·총액 UPDATE
9. 최신 예약을 조회해 응답하고 커밋
```

### Reservation 배타 락이 필요한 이유

연장은 현재 종료 시각을 기준으로 추가 슬롯 목록과 금액을 계산한다. 같은 예약에 두 연장이 동시에 이 계산을 하면 같은 출발점에서 서로 다른 결과를 만들 수 있다.

첫 번째 요청이 Reservation 배타 락을 잡으면 두 번째 요청은 기다린다. 첫 번째 요청이 커밋한 뒤 두 번째 요청은 변경된 종료 시각을 읽고, 자신이 보낸 `expectedEndTime`과 다르므로 `RESERVATION_STATE_CONFLICT`로 종료된다.

Reservation 배타 락이 있더라도 마지막 조건부 UPDATE를 유지한다. 취소·체크아웃처럼 같은 비관적 락을 사용하지 않는 경로와의 경합까지 `expectedEndTime`과 상태 조건으로 막기 위해서다.

### 연장 종료 시각 규칙

`ReservationTimePolicy.validateExtension`이 다음 규칙을 한곳에서 검증한다.

| 규칙 | 예시 |
| --- | --- |
| 새 종료 시각은 현재 종료 시각보다 늦어야 함 | `15:00 → 14:30` 거절 |
| 현재·새 종료 시각은 정확한 30분 경계여야 함 | `15:00 → 15:45` 거절. 초·나노초도 0이어야 함 |
| 기존 예약과 같은 날짜여야 함 | `9/20 23:30 → 9/21 00:30` 거절 |
| Space 운영 종료 시각을 넘지 않아야 함 | 22:00 마감 공간의 `21:30 → 22:30` 거절 |

운영 시작 시각은 다시 검사하지 않는다. 연장은 이미 존재하는 예약의 현재 종료 시각부터 뒤로 슬롯을 붙이는 동작이기 때문이다.

## 경합별 최종 방어선

한 가지 장치로 모든 동시성 문제를 해결하지 않는다. 각 경합의 최종 판정 지점은 다음과 같다.

| 경합 | 실행 순서 제어 | 최종 방어선 |
| --- | --- | --- |
| Space 수정 ↔ 결제 | Space 배타 락 ↔ 공유 락 | `space.version` 비교 |
| Space 수정 ↔ 연장 | Space 배타 락 ↔ 공유 락 | 잠금 후 읽은 운영시간으로 시간 정책 검증 |
| 결제 ↔ 결제 | 멱등 응답 조회, DB UPDATE 경합 | `status='HELD' AND now < hold_expires_at` 조건부 UPDATE |
| 연장 ↔ 연장 | Reservation 배타 락 | `end_time=:expectedEndTime` 조건부 UPDATE |
| 연장 ↔ 취소·체크아웃 | 트랜잭션 경합 | `end_time`과 `status IN ('CONFIRMED','IN_USE')` 조건부 UPDATE |
| 연장 ↔ 신규 예약 | 같은 슬롯 INSERT 경합 | `UNIQUE(space_id, slot_start)` |

추가 슬롯 INSERT, 크레딧 차감, Reservation UPDATE는 하나의 트랜잭션이다. 중간 단계가 실패하면 새 슬롯과 크레딧 변경을 포함해 전부 롤백되며 기존 예약과 기존 슬롯은 유지된다.

## 코드 위치

| 책임 | 파일·메서드 |
| --- | --- |
| 결제 처리 순서 | `ReservationPaymentConfirmService.confirm` |
| 연장 처리 순서 | `ReservationExtendService.extend` |
| 대상 Space·소유자 투영 조회 | `ReservationRepository.findTargetInfoById` |
| 대상 Space 투영 조회 | `ReservationRepository.findSpaceIdById` |
| Reservation 배타 락 | `ReservationRepository.findByIdForUpdate` |
| 결제 최종 조건부 전이 | `ReservationRepository.confirmIfHeldAndNotExpired` |
| 연장 최종 조건부 갱신 | `ReservationRepository.extendIfEndTimeMatches` |
| 연장 시간 정책 | `ReservationTimePolicy.validateExtension` |

## 검증 체계와 책임

동시성 제어는 단위 테스트와 다중 스레드 DB 통합 테스트로 책임을 분리해 검증한다.

- **단위 테스트 (`ReservationPaymentConfirmServiceTest`, `ReservationExtendServiceTest`)**:
  - `Clock`을 고정한 상태에서 소유권 선검증, 공유 락 호출 여부, `spaceVersion` 불일치 예외(`SPACE_VERSION_MISMATCH`), 조건부 UPDATE 실패 시 롤백(`RESERVATION_STATE_CONFLICT`), 연장 시간 경계값(`ReservationTimePolicy`)을 빠르게 검증한다.
- **다중 트랜잭션 통합 테스트 (`SpaceReservationLockIntegrationTest`)**:
  - Mock 환경에서 검증할 수 없는 실제 MySQL 격리 수준에서의 잠금 대기를 검증한다.
  - Testcontainers MySQL 환경에서 `CountDownLatch`와 별도 트랜잭션(`REQUIRES_NEW`) 스레드를 사용하여, Space 수정(배타 락)과 결제/연장(공유 락) 간의 상호 대기, 그리고 가격 변경 커밋 후 결제 시도의 버전 불일치 감지를 검증한다.

## 기존 슬롯 충돌 원칙

화면의 가용성 조회는 참고값일 뿐이다. 조회와 INSERT 사이에 다른 요청이 슬롯을 점유할 수 있으므로, 슬롯 충돌의 최종 진실은 `reservation_slot`의 `UNIQUE(space_id, slot_start)`다.

연장과 신규 예약이 같은 슬롯을 요청하면 먼저 INSERT를 커밋한 쪽만 성공한다. 현재 이용 중인 사용자에게 별도 우선권을 주지 않는다. 만료된 HOLD는 슬롯 확보 전에 정리하지만, 유효한 `HELD`, `CONFIRMED`, `IN_USE` 등의 점유는 동일하게 충돌로 처리한다.
