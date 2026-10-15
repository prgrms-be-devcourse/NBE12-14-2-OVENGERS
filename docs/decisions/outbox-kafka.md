# Outbox + Kafka — 이벤트 전달

> 상태: 초안 (구현 전). 작성: 2026-10-06. 열린 질문 결정 반영: 2026-10-06. 10/21 범위 결정 반영: 2026-10-15.

## 문제

- "자리가 났다", "충전됐다" 같은 사실을 다른 기능(대기열, 알림, 감사)에 전달해야 한다.
- DB 변경은 성공했는데 이벤트 발행이 실패하면(또는 반대) 두 곳이 어긋난다 (이중 쓰기 문제).

## 선택안 (초안): 3단계로 전달 방식만 교체한다

| 단계 | 전달 방식 | 얻는 것 |
| --- | --- | --- |
| 1 | 서버 내부 이벤트 (`@TransactionalEventListener`) | 새 기술 없이 대기열이 먼저 동작 ([waitlist](waitlist.md)) |
| 2 | `outbox_event` 테이블 + 서버 내부 relay | 이벤트 유실 방지 (변경과 같은 트랜잭션에 기록) |
| 3 | relay의 목적지를 Kafka로 교체 | 비동기, 재처리, 확장 |

컨슈머는 처음부터 중복 이벤트에 안전하게(멱등) 만든다. 그래야 단계가 바뀌어도 컨슈머 코드를 고치지 않는다.

## 발행 지점 (작성 시점의 코드 기준)

슬롯이 풀리는 곳은 아래뿐이다 (`deleteByReservationId` 등 삭제 호출 전체 검색 결과).

| 위치 | 상황 | 발행 |
| --- | --- | --- |
| `ReservationCancelService.cancel` — 환불·위약금·토큰 폐기·상태 이력 저장 뒤, 메서드 끝 | 본인 취소 | 한다 (`CANCELLED`) |
| `AdminReservationService.forceCancel` — 토큰 폐기·환불·상태 이력·감사 로그 저장 뒤, 응답 조립 전 | 관리자 강제 취소 | 한다 (`FORCE_CANCELLED`) |
| `ReservationHoldExpirationService.expireHold` — 상태 이력 저장 뒤, 전이가 일어난 경우(`updated == 1`)만 | 홀드 만료 (배치와 즉시 정리 두 경로 공통) | 한다 (`HOLD_EXPIRED`) |
| `ReservationBatchProcessor.markNoShow` | 노쇼 (시작+15분 경과) | 하지 않는다: 이미 시작 시각이 지난 구간이라 대기자에게 의미가 없다 |

- 기록은 `SlotReleasedRecorder.record`(`Propagation.MANDATORY`)가 호출자 트랜잭션 안에서 INSERT한다. 트랜잭션이 없으면 `IllegalTransactionStateException`으로 실패하고, 롤백되면 이벤트도 함께 사라진다.
- `expireHold`도 `MANDATORY`라서 호출자 트랜잭션 안에서만 돈다. 여기에 기록하면 배치 경로와 다른 사용자의 슬롯 확보(`ReservationSlotService.secureSlots`) 중 즉시 정리 경로가 모두 같은 원자성을 얻는다.
- 위치는 "각 서비스 메서드의 마지막 쓰기"이지 "호출자 트랜잭션 전체의 마지막 쓰기"가 아니다. 즉시 정리 경로에서는 `expireHold` 뒤에 `secureSlots`의 새 슬롯 INSERT가 이어진다. 롤백 원자성은 같고, 잠금 관점의 영향은 [02 설계 문서 §1 relay 제약](../plans/waitlist-outbox/02-outbox-kafka-design.md)에 적었다.
- 체크아웃과 자동 퇴실은 슬롯을 지우지 않는다. 대상이 아니다.
- 페이로드는 `eventId`, `reservationId`, `reason`, `occurredAt` 4필드다. `spaceId`는 페이로드에 넣지 않고 `partition_key` 컬럼에만 둔다(`aggregate_id` 컬럼도 만들지 않는다). 취소·만료 후에도 예약 행은 남으므로 컨슈머가 `startTime`, `endTime`을 조회한다.

## 지켜야 할 규칙

- **`outbox_event`에는 FK를 걸지 않는다.** 이 코드베이스는 FK 검사의 공유 잠금(S) 때문에 Member/Space 잠금 순서 데드락을 겪었고 주석에 해법이 남아 있다 (`ReservationHoldService`, `AdminReservationService.forceCancel`). 독립 테이블이면 새 잠금 경로가 생기지 않는다.
- relay는 `ReservationCompletionScheduler`의 `processEach` 패턴(건별 트랜잭션, 한 건 실패해도 계속)을 따른다.
- 컨슈머는 `processed_event(event_id UNIQUE)`로 중복을 걸러낸다.
- Kafka는 로컬 docker compose(KRaft 단일 노드)로만 실행한다. 팀 EC2는 Gradle 테스트가 OOM으로 죽은 이력이 있어 얹지 않는다.

## 10/21 범위 (2026-10-15)

이번 구현 범위(1·2단계 생략, 폴링 Outbox → Kafka, `SlotReleased` 1종, 멱등 컨슈머, 테스트 방식)는 [plans/waitlist-outbox](../plans/waitlist-outbox/00-waitlist-outbox-plan.md)에 둔다. 발행 지점 3곳, FK 금지, 토픽 분리, 파티션 키 `spaceId`, 로컬 compose 결정은 이 문서 그대로 유지한다.

## 검증 계획

- Kafka가 내려가 있어도 취소는 성공하고, 복구 후 이벤트가 이어서 발행된다.
- 같은 이벤트가 중복 전달돼도 컨슈머 효과는 한 번이다.
- 발행 지점 세 곳의 기존 테스트(`ReservationCancelConcurrencyTest`, `ReservationCompletionSchedulerTest`, `AdminReservationControllerTest` 등)가 그대로 통과한다.

## 결정 (2026-10-06)

- **토픽은 이벤트 종류별로 나눈다.** 지금 이벤트는 하나라 토픽은 `slot-released` 하나로 시작하고, 이벤트가 늘면 토픽을 추가한다. 하나의 토픽에 타입 필드를 두는 방식은 쓰지 않는다.
- **파티션 키는 `spaceId`다.** 같은 공간의 이벤트는 같은 파티션에서 순서가 보장된다.
- **로컬 환경:** `backend/docker-compose.yml`에 Kafka(KRaft 단일 노드)를 별도 compose 프로필로 추가한다. 프로필을 켜지 않으면 기존 MySQL만 뜬다. 저사양 EC2에는 올리지 않는다.

## 열린 질문

- (없음)

## 검증 결과

(구현 후 작성)
