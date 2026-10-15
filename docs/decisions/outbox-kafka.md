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

## relay 결정 (2026-10-15, 3단계)

설계 문서([02 §1](../plans/waitlist-outbox/02-outbox-kafka-design.md))에 이미 있는 폴링 방식(`@Scheduled`, id 순 N건), 건별 트랜잭션, 잠금 없는 후보 조회 + PK 조건부 UPDATE, at-least-once, 인스턴스 1개 가정은 그대로 따른다. 이 문서에 빠져 있던 것만 더한다.

- **FAILED 상태를 도입하지 않는다.** 계속 실패하는 행도 `PENDING`으로 두고 `attempts`만 올린다(`outbox_event.status`는 `PENDING`/`PUBLISHED` 두 값뿐). 상한을 넘겨 포기하는 로직이 없으므로 브로커가 오래 죽어 있어도 복구 시 이어서 발행된다.
- **Kafka 전송은 트랜잭션 밖에서 한다.** relay 자체는 트랜잭션을 열지 않고, `markPublished`/`increaseAttempts` 두 UPDATE가 각각 자기 트랜잭션이다. 전송 중 DB 잠금을 쥐지 않는다.
- **타임아웃 불변식:** `slotkey.kafka.send-timeout-ms`(전송 대기, 6000) **>** `delivery-timeout-ms`(5000) **>** `request-timeout-ms`(3000). 값은 `application.yml`의 `slotkey.kafka` 한 곳에서만 정의하고(`max-block-ms`=3000 포함) 프로듀서 설정(`spring.kafka.producer.properties`)이 이를 참조한다. 전송 대기가 delivery.timeout보다 짧으면 대기 타임아웃으로 "실패" 처리된 레코드가 프로듀서 버퍼에 남아 브로커 복구 뒤 뒤늦게 전달되고, 다음 relay 라운드의 재전송과 겹쳐 같은 이벤트가 토픽에 두 번 들어간다. **첫 수동 E2E에서 전송 대기 5초 < delivery.timeout 8초일 때 같은 `eventId`가 두 번 들어간 것을 실제로 관찰**했고, 값을 정렬한 뒤 재현되지 않았다. 기본값이 부등식을 지키는지는 단위 테스트(`KafkaTimeoutDefaultsTest`)가 확인한다.
- **중복 전달은 at-least-once의 정상 범위다.** 불변식을 지켜도 전송 성공 후 `markPublished` 직전 장애 등으로 같은 이벤트가 두 번 나갈 수 있다. 막으려고 하지 않고 컨슈머 멱등 처리(`processed_event`, 4단계)로 흡수한다. 전송은 성공했는데 `markPublished`가 실패하는 경우도 같다 — 행이 `PENDING`으로 남고 `attempts`가 오른 채 다음 라운드에 다시 발행된다(로그 메시지로 "전송 실패"와 구분하며, `OutboxRelayTest`가 이 분기를 고정한다).
- **기본은 꺼짐.** `slotkey.kafka.enabled=false`(환경변수 `SLOTKEY_KAFKA_ENABLED`)이면 `OutboxRelay`와 `KafkaOutboxPublisher` 빈이 생성되지 않는다. 브로커가 없는 팀 EC2 배포가 기동에 실패하지 않게 하기 위해서다. 브로커 주소는 `KAFKA_BOOTSTRAP_SERVERS`(기본 `localhost:9092`).

## 컨슈머 결정 (2026-10-16, 4단계)

계획 문서([02 §1](../plans/waitlist-outbox/02-outbox-kafka-design.md))의 컨슈머 방향(얇은 리스너 + `SlotReleasedHandler`, `processed_event` 멱등, 알림 대상 재검사)은 그대로 따른다. 여기에는 문서에 비어 있던 값만 적는다.

- **이벤트 payload (실제 2단계 코드 `SlotReleasedRecorder.Payload` 기준):** `eventId`, `reservationId`, `reason`, `occurredAt` 4필드. `spaceId`와 구간은 payload에 없다. 컨슈머가 `reservationId`로 예약 행을 조회해 `spaceId`, `startTime`, `endTime`을 얻는다(취소·만료 뒤에도 예약 행은 남는다).
- **컨슈머 그룹 id:** `slotkey-waitlist-notifier`. 용도가 이름에 드러나게 하고, 나중에 같은 토픽을 감사 등 다른 컨슈머가 읽어도 오프셋이 섞이지 않게 한다. `processed_event.consumer`에는 같은 의미로 `waitlist-notifier`를 쓴다.
- **오프셋 시작 위치:** `auto-offset-reset=earliest`. 컨슈머가 relay보다 늦게 떠도 이벤트를 잃지 않는다. 과거 이벤트가 재생돼도 알림 대상을 "지금" 기준으로 다시 고르므로 틀린 알림은 나가지 않는다.
- **실패 시 재시도:** Spring Kafka 3.2.4의 기본 `DefaultErrorHandler`는 `FixedBackOff(0, 9)`(간격 없이 총 10회 시도 후 로그만 남기고 건너뜀)이다(`SeekUtils.DEFAULT_BACK_OFF`를 바이트코드로 확인). 즉시 10번 두들기면 DB 순간 장애에 의미가 없으므로 `FixedBackOff(1000ms, 2회 재시도)`로 짧게 제한하고, 소진되면 건너뛴다. 해석 불가능한 payload(`IllegalArgumentException`)는 재시도해도 같으므로 재시도 없이 건너뛴다. **DLT(dead-letter topic)는 3차로 미룬다** — 건너뛴 이벤트는 로그에만 남고 복구 수단이 없다는 한계를 안고 간다.
- **멱등 처리:** 한 트랜잭션 안에서 ① `INSERT IGNORE INTO processed_event(consumer, event_id)`(UNIQUE(consumer, event_id))를 먼저 실행해 영향 행이 0이면 이미 처리한 이벤트로 보고 아무것도 하지 않는다 ② 알림 INSERT. 둘이 같은 트랜잭션이므로 알림 저장이 실패하면 `processed_event`도 롤백되어 재전달 때 다시 처리된다. 알림 테이블에도 UNIQUE(event_id, waitlist_entry_id)를 걸어 이중으로 막는다.

- **한계 — 취소와 컨슈머가 경합하면 취소 직후에도 알림이 한 건 생길 수 있다:** 핸들러가 후보(활성 WAITING)를 읽은 뒤 사용자의 대기 취소가 커밋되면, `markNotified`는 WAITING이 아니라 0건을 갱신하지만 알림 저장은 이미 끝나 있어 취소한 사용자에게 알림이 저장된다(`markNotified` 반환값을 보지 않는다). 허용한다: 알림은 힌트일 뿐이고 최종 판정은 슬롯 UNIQUE이며, 이 경합을 막으려면 후보 행에 락을 걸어야 하는데 3단계의 "락 대기 없음" 원칙(취소·강제 취소·홀드 만료 트랜잭션이 relay나 컨슈머와 락을 기다리지 않는다)을 깨기 때문이다. 재검토 조건: 취소 직후 알림 민원이 관측될 때(그때는 `markNotified`를 먼저 호출하고 반환값 1일 때만 알림을 저장하는 순서 변경이 후보다).
- **한계 — `INSERT IGNORE`는 중복 외 오류도 삼킬 수 있다:** `event_id VARCHAR(36)`에 36자를 넘는 값이 들어오면 잘려 저장되어, 앞 36자가 같은 서로 다른 `event_id`를 중복으로 오판하는 것이 재현되었다(MySQL 8.4 기본 strict 모드). 현재는 수용한다: `event_id`는 우리 outbox가 만드는 36자 UUID이고 나머지 컬럼은 코드 상수·시각뿐이다. 재검토 조건: 외부 입력이 이벤트 id를 만들게 될 때 — 그때 리스너에서 길이(36자 초과)를 검증해 `IllegalArgumentException`(비재시도)으로 거른다.

## 열린 질문

- (없음)

## 한계

- **FAILED 미도입의 한계(head-of-line 막힘):** 계속 실패하는 행이 `PENDING`에 남아 매 라운드 재시도되고, relay는 id 순으로 한 번에 N건(`batch-size`, 기본 50)만 읽는다. 영구 실패 행이 N건 이상 쌓이면 그 뒤의 새 이벤트가 읽히지 않아 발행되지 못한다. 현재 페이로드가 4필드 고정이라 가능성은 낮지만 구조상 존재한다.
  - 재검토 조건: `attempts`가 임계치 이상인 `PENDING` 행이 관측될 때.
  - 후속안: FAILED 상태(또는 DLT) 도입, 또는 `attempts` 기반 백오프(실패 횟수가 많은 행의 재시도 간격을 늘리거나 배치에서 후순위로 미룸). 3차 범위.
- **단일 스케줄러 스레드 점유:** 스케줄러 풀이 기본 1개라 `ReservationCompletionScheduler`, `ChargeOrderRecoveryScheduler`와 같은 스레드를 쓴다. relay는 한 라운드에 `batch-size`건을 순차 전송하므로 브로커 장애 시 한 라운드가 최대 `batch-size × (max-block-ms + send-timeout-ms)`(기본 50 × 9초 = 450초 = 7.5분)까지 걸려 같은 스케줄러의 다른 작업(예약 완료·홀드 만료 정리, 충전 복구)을 지연시킬 수 있다. 기본값(꺼짐)이라 현재 배포에는 영향이 없다. 재검토 조건: Kafka를 켠 환경에서 브로커 장애 중 스케줄러 지연이 관측될 때.
  - shutdown 중 인터럽트가 걸려도 relay는 남은 배치를 계속 처리하므로, 그 건들이 전송 실패로 계산되어 `attempts`가 오를 수 있다.
- 브로커 장애 중에는 매 라운드 실패 로그가 건마다 남는다(로그 소음). 백오프가 없다.
- 인스턴스 2개 이상이면 같은 행을 둘이 발행해 중복 전송된다(PK 조건부 UPDATE는 DB 반영만 한 번으로 만든다). 다중 인스턴스는 범위 밖이다.

## 검증 결과

(구현 후 작성)
