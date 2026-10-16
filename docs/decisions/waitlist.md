# 예약 대기열과 빈자리 알림

> 상태: 10/21 범위 구현 완료 (1~4단계), 5단계 문서 정리 중. 작성: 2026-10-06. 열린 질문 결정 반영: 2026-10-06. 10/21 범위 결정 반영: 2026-10-15. 4단계(알림)·5단계(문서) 반영: 2026-10-16.

## 한 줄 요약

자리가 나면 대기자 전원에게 "비었다"는 알림을 보내고 예약은 기존 슬롯 UNIQUE로 선착순 처리한다 — 순번을 약속하는 자동 선점권은 예약 동시성 경로를 건드리므로 3차로 미룬다.

## 1. 문제와 배경

- 원하는 시간이 이미 예약돼 있으면 사용자는 직접 계속 확인해야 한다.
- 자리가 나면 "먼저 누르는 사람"이 가져가는 경쟁이 생긴다.
- 제약: 마감 2026-10-21(정처기 10/25 직전 주). 예약 동시성 규칙(HOLD → 결제, 슬롯 `UNIQUE(space_id, slot_start)`)과 Member/Space 잠금 순서 데드락 이력(FK 금지)을 건드리지 않아야 한다. 이 기능은 Outbox + Kafka([outbox-kafka](outbox-kafka.md))의 첫 소비자이기도 하다.

## 2. 선택과 이유

- **선택한 방법:** 전원 알림 + 선착순(방식 B). 점유 중인 구간에만 대기를 걸 수 있고(`WAITLIST_NOT_NEEDED`), 슬롯이 풀리면 **그 시점의 DB로 다시 고른** 대기자 전원에게 알림을 저장한다. 예약은 기존 `POST /reservations`로 하고 판정은 슬롯 UNIQUE가 한다. 알림은 힌트다.
- **이유:**
  1. 순번을 지키려면(방식 A) 선점 구간 동안 대기자가 아닌 사람의 직접 예약을 막아야 하고, 그러면 핵심인 예약 동시성 경로를 건드린다. B는 `createHold`를 한 줄도 고치지 않는다([plans/waitlist-outbox/00](../plans/waitlist-outbox/00-waitlist-outbox-plan.md) §2).
  2. A는 부수 상태(1순위 HOLD 생성, 만료 시 이월, 무응답·정지·크레딧 부족자 처리)가 늘어 일정에 비해 무겁다.
  3. 테스트가 "알림 대상 선정"과 "멱등"에 집중된다.
- **4단계(알림)에서 정한 세부 선택** (근거: `lab/notes/waitlist-outbox/step-4.md` 자율 결정 기록):
  - **알림 대상은 payload가 아니라 지금의 DB로 고른다.** 활성 `WAITING`·시작이 미래·해제 구간과 겹침 + 회원 `ACTIVE` + 본인의 겹치는 점유 예약 없음 + 신청 구간 **전체**가 비어 있음. 홀드 만료 직후 다른 사람이 재점유한 슬롯에 알림이 가지 않게 하기 위함이다.
  - **`waitlist_entry`에 `@DynamicUpdate`를 붙이고 알림 시각은 조건부 UPDATE로 기록한다.** 유실의 원인은 취소의 flush가 전체 컬럼 UPDATE를 하며 컨슈머가 쓴 `last_notified_at`을 읽은 시점의 값(null)으로 되돌리는 것이다. 컨슈머 쪽 조건부 UPDATE(`WHERE id AND status='WAITING'`)만으로는 취소 쪽 덮어쓰기를 못 막는다.
  - **`notification`에 `type` 컬럼을 두지 않고** 알림 종류는 하나(자리가 비었다)로 둔다. 읽음은 `read_at IS NULL`로 표현하고 인덱스는 목록용 `(member_id, id)`와 개수용 `(member_id, read_at)` 두 개다.
  - **문구는 생성 시점에 문자열로 저장한다**(공간 이름·구간·"선착순이며 예약이 보장되지 않는다").

## 선택안 (초안)

> 3·4·6번은 자동 선점권 모델이라 **3차로 이월**했다. 10/21에 구현하는 범위는 아래 "10/21 범위"와 [plans/waitlist-outbox](../plans/waitlist-outbox/00-waitlist-outbox-plan.md)을 따른다.

1. **대기 신청:** `waitlist_entry(member, space, 시작~종료, created_at, status)`. 상태는 대기, 제안됨, 완료, 만료, 취소.
2. **트리거:** 슬롯이 풀리는 이벤트 ([outbox-kafka](outbox-kafka.md)의 발행 지점). 1단계에서는 서버 내부 이벤트로 시작한다.
3. **[3차 이월] 제안:** 해당 공간·시간대의 대기자를 `created_at` 순으로 보고, 신청 범위의 **모든 30분 슬롯이 비었을 때만** 1순위에게 기존 `ReservationHoldService.createHold(memberId, spaceId, start, end)`를 호출한다. 컨슈머가 자기 트랜잭션에서 호출하므로 취소 트랜잭션의 잠금 순서와 얽히지 않는다.
4. **[3차 이월] 충돌:** 그 사이 다른 사용자가 먼저 잡아 `RESERVATION_SLOT_CONFLICT`가 나면 에러가 아니라 "이번엔 못 잡음"으로 정상 종료한다. 최종 판정은 슬롯 유니크 제약이다.
5. **알림:** `notification` 테이블에 저장하고 조회 API로 읽는다(10/21은 "자리가 났습니다" 알림. "10분 안에 결제하세요"는 제안 모델의 문구라 3차). SSE나 이메일은 선택 확장.
6. **[3차 이월] 연쇄:** 제안된 HOLD가 결제되지 않아 만료되면 같은 `expireHold` 경로로 다시 이벤트가 발행되어 다음 순번으로 넘어간다. 추가 코드가 필요 없다.

## 규칙

- 시작 시각이 지난 대기 항목은 무효 처리한다 (강제 취소가 IN_USE 상태에서도 슬롯을 풀기 때문).
- 부분 매칭(신청 범위 일부만 비었을 때), 우선순위 정책(등급 등)은 이번 범위에서 제외한다.
- 제안은 새 예약 로직이 아니라 **기존 HOLD 흐름 재사용**이다. 결제는 사용자가 직접 한다.

## 결정: 정지 회원은 대기열 제안에서 건너뛴다

> 10/21 범위에서는 제안이 없으므로 "알림만 건너뛰고 항목은 유지"로 단순화한다(아래 "10/21 범위"). 이 절은 3차 자동 선점권 때 다시 쓴다.

- 제안 단계(소비자)에서 대기자의 현재 회원 상태를 DB에서 조회해 `ACTIVE`가 아니면 그 항목을 건너뛰고 다음 대기자에게 넘긴다. 기존 `createHold`는 수정하지 않는다.
- 이유: 소비자는 백그라운드에서 돌아 JWT가 없으므로 "이미 발급된 토큰이 5분간 유효"라는 규칙 3의 창이 적용되지 않는다. 대기 중에 정지된 회원에게 슬롯을 10분간 잡아 주면 다른 대기자만 밀린다.
- 건너뛴 항목의 처리(대기 유지 vs 만료)는 구현 시 정한다. → 2026-10-15: 10/21 범위는 **대기 유지**로 결정.
- 검증: 정지된 대기자가 앞 순번일 때 다음 순번에게 제안되는지, 정지 해제 후 정상 제안되는지 테스트.

## 결정 (2026-10-06)

- **[3차 이월] 크레딧이 부족한 대기자에게도 제안한다.** 제안 단계에 잔액 검사를 넣지 않는다. 결제는 사용자가 직접 하고, 못 하면 10분 뒤 HOLD가 만료되어 다음 순번으로 넘어간다.
- **같은 신청의 중복은 DB로 막는다:** `waitlist_entry`에 `(member, space, start, end)` UNIQUE. → *(2026-10-15 정정: 구현은 `(member_id, space_id, start_time, end_time, active)` **5칸**이다. `active`는 WAITING일 때만 1이고 그 밖에는 NULL이어서, 활성 신청은 구간당 1건으로 막으면서 취소한 뒤 같은 구간을 다시 신청할 수 있다. 4칸 UNIQUE는 취소 이력 때문에 재신청이 영구히 막혔다 — `plans/waitlist-outbox/01-waitlist-rules.md` §1, `lab/notes/waitlist-outbox/step-1-fix-1.md`)*
- **사용자당 활성 대기(대기·제안됨)는 최대 3건**이다. 이 건수 제한은 애플리케이션에서 검사하므로 동시 요청이 겹치면 3건을 넘을 수 있고, 이는 수용한다.

## 3. 기각한 대안과 이유

> 10/21 범위(전원 알림 + 선착순)를 고른 판단이다. 비교 표와 자세한 근거는 [plans/waitlist-outbox/00](../plans/waitlist-outbox/00-waitlist-outbox-plan.md) §2에 있고, 여기에는 결론만 옮긴다.

| 대안 | 장점 | 기각한 이유 |
| --- | --- | --- |
| **A. 순번 자동 선점권** (자리가 나면 1순위에게 HOLD를 만들어 준다) | 공정성이 가장 높고 순번이 지켜지는 약속이 된다 | 대기자가 아닌 사람의 **직접 예약 경로를 막지 않는 한 1순위도 자리를 보장받지 못한다**. 보장하려면 선점 구간 동안 일반 예약을 막아야 하고, 그러면 이 프로젝트의 핵심인 예약 동시성 경로를 건드린다. 부수 상태(1순위 HOLD 생성, 만료 시 다음 순번 이월, 무응답·정지 회원·크레딧 부족자 처리)도 함께 늘어난다 → **3차로 이월** |
| **C. 순번대로 시차 알림** (1순위에게 먼저, N분 뒤 다음 사람) | 기존 예약 경로를 건드리지 않으면서 순번에 의미를 준다 | 지연 스케줄링이 상태로 남고(언제 누구에게 보냈는지), 알림이 늦게 가는 사이 자리가 채워지면 순번의 의미가 다시 사라진다. 10/21 일정(정처기 10/25 직전 주)에 비해 비용이 크다 → 3차에서 재검토 |
| 대기 신청 없이 **점유 중인 슬롯도 모두 예약 가능**하게 열기 | 테이블·API가 늘지 않는다 | 슬롯 UNIQUE 충돌이 사용자에게 그대로 노출되고, "기다린다"는 사용자 의도를 저장할 곳이 없어 알림을 보낼 수 없다 |
| 중복 신청을 **애플리케이션 선조회**로 막기 | 에러 코드 변환이 단순해진다 | 조회–INSERT 사이 경합에서 중복이 통과한다. 이 프로젝트의 기준(판정은 DB 제약이 한다)과도 어긋난다 → UNIQUE로 막고 제약 위반을 `WAITLIST_DUPLICATE`로 변환 |
| 알림 시각을 **조건부 UPDATE만**으로 기록(엔터티는 그대로) | 추가 애너테이션이 없다 | 취소의 전체 컬럼 UPDATE가 기록된 `last_notified_at`을 되돌리는 쪽은 막지 못한다 → `@DynamicUpdate`와 함께 쓴다 |
| 낙관적 락(`@Version`)으로 갱신 유실 방지 | 모든 필드의 충돌을 잡는다 | 모든 쓰기에 재시도 처리가 필요해 과하다. 충돌하는 필드는 사실상 `last_notified_at` 하나뿐이다 |
| 조건부 UPDATE에 **이전 값 비교**(`last_notified_at <=> :prev`) | 중복 갱신을 막는다 | 같은 대기 항목에 서로 다른 이벤트가 연달아 알림을 보내는 것은 정상이고, 중복은 `processed_event`·`UNIQUE(event_id, waitlist_entry_id)`가 막는다. 비교를 넣으면 정상 재알림이 실패한다 |
| `notification.type` 컬럼 유지(01 문서 초안의 `SLOT_AVAILABLE`) | 알림 종류 확장에 대비한다 | 종류가 하나뿐이다. 두 번째 종류가 생길 때 마이그레이션으로 추가한다 |
| 취소 시 **행을 삭제**해 재신청을 허용하기 | 4칸 UNIQUE를 그대로 둘 수 있다 | 누가 언제 걸었다 취소했는지가 사라져 알림 대상 선정 논의의 근거가 남지 않는다 → `active` 칸을 UNIQUE에 넣고 행은 이력으로 보존 |

## 4. Trade-off

| 얻는 것 | 잃는 것(비용·위험) | 완화책 |
| --- | --- | --- |
| 기존 예약 경로(HOLD → 결제, 슬롯 UNIQUE)를 **한 줄도 건드리지 않는다** | 알림을 받은 N명이 동시에 시도해 N-1명이 `RESERVATION_SLOT_CONFLICT`를 받는다 | 알림 문구에 선착순이며 보장이 아님을 명시한다. 3차에서 C 또는 A로 올린다 |
| 1단계가 새 인프라(Kafka) 없이 독립적으로 완성된다 | Kafka가 늦어지면 "신청은 되는데 알림이 없는" 반쪽 기능 기간이 생긴다 | 발행을 포트(`OutboxPublisher`)로 분리해 Fake로 흐름을 먼저 완성하고 구현만 교체한다 |
| 순번 계산이 질의 하나(겹치는 선행 WAITING 수)로 끝난다 | 부분 겹침에서는 사람마다 기준 집합이 달라 **전체 순서가 아니다** | "신청 순서(참고용)"로 표기하고 그 한계를 01 문서에 적었다 |
| 중복·활성 1건을 DB가 판정한다(UNIQUE) | `active` 칸과 `status`가 어긋날 수 있다 | `chk_waitlist_entry_active` CHECK(NULL 안전 비교)로 DB에서 막고, 전이 시 `active`를 함께 비우도록 4단계 설계 문서에 선행 조건으로 적었다 |
| 활성 3건 제한을 애플리케이션에서 검사해 단순하다 | 동시 요청이 겹치면 3건을 넘길 수 있다 | 돈·슬롯이 움직이지 않아 손해가 없으므로 수용한다(01 문서 §1에 명시) |
| 알림 대상을 지금의 DB로 다시 고르므로 틀린 알림이 나가지 않는다 | 후보 N명에 대해 질의 약 3N+1회, 건별 회원 조회 | 같은 공간 대기자가 소수인 현재 규모에서는 수용한다. 수십 명 이상이면 재검토 |
| 같은 대기 항목에 이벤트마다 알림이 쌓여 재알림 정책이 필요 없다 | 슬롯이 여러 번 풀리면 같은 사람에게 알림이 여러 건 간다(쓰로틀 없음) | 스키마 변경 없이 3차에 쓰로틀을 넣을 수 있다(`last_notified_at` 기록은 이미 있다) |
| 점유 판정을 예약 경로의 질의로 재사용한다 | waitlist가 reservation에 의존한다 | 의존 방향을 포트(`ReservationOccupancyProvider`) + 어댑터로 뒤집어, 예약 쪽이 waitlist를 모르게 두고 기존 `OccupiedSlotProvider` 관례를 따랐다 |

## 6. 한계와 재검토 조건

- **`EXPIRED` 전이가 없다**(4단계 범위). 구간이 지난 `WAITING` 행은 남는다. 그래서 활성 판정을 `status = WAITING AND start_time > now`로 정의해 지난 행이 3건 제한·순번·내 목록에 끼지 않게 했다. 4단계에서 스윕을 넣을 때 `active`도 함께 비워야 한다.
- **알림은 보장이 아니다.** 선착순이라 오래 기다린 사람이 불리할 수 있다. 대기자 수가 늘어 "알림을 받아도 못 잡는다"는 불만이 반복되면 C(시차 알림) → A(선점권) 순으로 올린다.
- **인스턴스 1개 가정.** relay 폴링과 `@Scheduled`가 다중 인스턴스에서 중복 실행된다. 다중화하면 분산 락이나 CDC로 바꾼다(3차).
- **대기 항목은 공간·구간 단위이고 알림은 1종(`SLOT_AVAILABLE`)이다.** 우선순위(등급), 부분 매칭, SSE·메일은 범위 밖이다.
- **취소 직후에도 알림이 한 건 생길 수 있다**: 컨슈머가 후보를 읽은 뒤 사용자의 대기 취소가 커밋되면 알림이 저장된다. 알림은 힌트이고 최종 판정은 슬롯 UNIQUE라 허용하며, 후보 행에 락을 걸지 않는 것은 3단계의 "락 대기 없음" 원칙 때문이다([outbox-kafka](outbox-kafka.md) 컨슈머 결정의 한계).
- **`GET /notifications`의 `sort` 파라미터가 "최신순 고정"을 깰 수 있다(3차):** 컨트롤러가 `Pageable`을 그대로 받아 `?sort=createdAt,asc`가 메서드 이름의 정렬(`id` 내림차순)에 합쳐질 수 있고, 존재하지 않는 속성은 예외로 이어질 수 있다. 기존 `InquiryController`도 같은 패턴이라 프로젝트 관례와는 같다. 정렬 파라미터를 무시하거나 허용 목록으로 제한할지는 3차에서 정한다.
- **읽음 처리가 없다.** `notification.read_at`은 항상 NULL이고 안 읽은 개수는 줄지 않는다. SSE·메일과 함께 3차에서 정한다.
- **알림 문구에 공간 이름을 그대로 넣는다.** 이름이 이미 `[샘플] …`처럼 대괄호로 시작하면 `[[샘플] …]`로 보인다(로컬 E2E에서 확인, 표시만의 문제).
- **재검토 트리거**: ① 다중 인스턴스 배포 ② 대기 신청량이 공간당 수십 건 규모 ③ 알림 → 예약 성공률이 낮게 측정될 때 ④ 자동 선점권을 3차에서 구현할 때.

## 10/21 범위 (2026-10-15)

이번 구현 범위(전원 알림 + 선착순, 신청 규칙, 본인 점유자 차단, 구현 순서)는 [plans/waitlist-outbox](../plans/waitlist-outbox/00-waitlist-outbox-plan.md)에 둔다. 이 문서의 선택안 3·4·6번과 그에 딸린 결정은 자동 선점권 모델이라 3차로 이월했다. 정지 회원은 10/21 범위에서 "알림만 건너뛰고 항목 유지", 알림 후 항목 상태는 WAITING 유지로 정했다.

## 열린 질문

- (해소, 2026-10-15) 건너뛴 정지 회원의 대기 항목은 대기로 유지한다.
- (해소, 2026-10-16) 같은 대기 항목에 이벤트마다 알림이 쌓이는 것은 허용하고 쓰로틀은 3차로 미룬다. 읽음 처리·SSE·메일도 3차다.

## 5. 검증

10/21 범위(1~4단계)의 검증 계획과 결과는 아래 "검증 계획"·"검증 결과"에 있다. 계획은 3차 자동 선점권 기준이 먼저 오고, 10/21 범위의 결과는 단계별로 이어서 적었다.

## 검증 계획 (3차 자동 선점권 모델 기준, 10/21 범위의 검증은 plans/waitlist-outbox 계획의 검증 계획 참고)

- 같은 "자리 났음" 이벤트가 두 번 와도 대기자 한 명에게만 HOLD가 생긴다.
- 대기자 3명일 때 신청 순서대로 제안되고, 홀드가 만료되면 다음 사람에게 넘어간다.
- 제안 직전에 탈퇴한 회원은 건너뛴다.
- 취소와 직접 예약이 동시에 들어와도 슬롯은 한 명만 가진다.
- 이벤트 전달이 끊겨도 취소는 성공하고, 복구 후 대기자 처리가 이어진다.

## 검증 결과

### 1단계 대기 신청 (2026-10-15, 구현 세션 / 검증은 별도 세션 필요)

보고 전문은 [lab/notes/waitlist-outbox/step-1.md](../lab/notes/waitlist-outbox/step-1.md)에 있다. 구현 범위는 `waitlist_entry`(Flyway V17), `POST /api/v1/waitlist`, `DELETE /api/v1/waitlist/{id}`, `GET /api/v1/waitlist/me`이고 Kafka·Outbox·알림은 포함하지 않는다.

- `WaitlistServiceIntegrationTest`(Testcontainers MySQL 8.4) 통과(1단계 19건 → fix-1·fix-2를 거쳐 32건). 구간 전부 비면 `WAITLIST_NOT_NEEDED`, 일부 점유면 성공, 같은 공간·겹치는 시간의 본인 **만료되지 않은** `HELD`/`CONFIRMED`/`IN_USE` 예약은 `WAITLIST_ALREADY_HOLDING`, 다른 공간이나 겹치지 않는 시간은 성공, UNIQUE 중복은 `WAITLIST_DUPLICATE`, 활성 4건째는 `WAITLIST_LIMIT_EXCEEDED`, 타인 대기 취소는 `FORBIDDEN_NOT_OWNER`, 정지는 `ACCOUNT_INACTIVE`·탈퇴는 `ACCOUNT_WITHDRAWN`, 신청 순서는 겹치는 선행 `WAITING` 건수 + 1이며 취소되면 당겨진다.
- `WaitlistControllerTest`(`@WebMvcTest`) 6건 통과. 201/204/200, `VALIDATION_FAILED`(400), 토큰 없음 401, `FORBIDDEN_NOT_OWNER` 전파.
- 전체 회귀: `./gradlew cleanTest test` → fix-2까지 반영한 현재 **87 클래스 697 tests, 실패 0, 에러 0**(1단계 시점은 85 클래스 679 tests였다). 기존 예약 동시성·취소·스케줄러 테스트는 그대로 통과했고 예약 경로 코드는 변경하지 않았다.
- 인덱스: `reservation` 12,000행 + `ANALYZE TABLE` 기준 EXPLAIN에서 본인 겹침 조회가 검사 행 1,240 → **64**로 줄어, V17에 `(member_id, space_id, status, start_time)` 인덱스를 추가한 판단이 맞음을 확인했다(수치·쿼리는 보고서 6절).
- 동시 신청 경계 테스트는 `WaitlistApplyConcurrencyTest` 1건으로 **실측했다**. 같은 회원·같은 공간·같은 구간에 `apply`를 **5스레드 동시 호출**하면 성공이 정확히 1건, 나머지 4건은 `WAITLIST_DUPLICATE`이고, `waitlist_entry`의 `WAITING` 행과 `active IS NOT NULL` 행이 각각 1건이다 — 판정자는 `UNIQUE(member_id, space_id, start_time, end_time, active)` 하나다. 반면 점유 검사–INSERT 경합과 활성 3건 초과는 "수용"으로 정한 결정([plans/waitlist-outbox](../plans/waitlist-outbox/01-waitlist-rules.md) §1)이라 테스트하지 않는다(돈도 슬롯도 움직이지 않는 경로다).
- 위 두 건(본인 점유 검사가 만료된 `HELD`를 점유로 본 것, UNIQUE에 status 성격의 칸이 없어 취소 후 같은 구간 재신청이 막힌 것)은 같은 날 fix-1에서 해결했다([보고서](../lab/notes/waitlist-outbox/step-1-fix-1.md)). 점유의 기준을 예약 경로와 한 질의로 맞춰(`CONFIRMED`·`IN_USE`이거나 만료 전 `HELD`) 만료된 홀드는 점유로 보지 않고, `waitlist_entry`에 `active`(WAITING이면 1, 아니면 NULL)를 두고 UNIQUE를 `(member_id, space_id, start_time, end_time, active)`로 바꿔 취소 이력을 남기면서 재신청을 허용한다. 본인 점유 차단에서 `COMPLETED`는 제외하되 슬롯 점유 판정에는 포함한다(체크아웃이 슬롯을 지우지 않으므로).

### fix-1 (2026-10-15) — 점유 기준 통일과 재신청 허용

[보고서](../lab/notes/waitlist-outbox/step-1-fix-1.md). 1단계 보고서가 리뷰 대상으로 남긴 두 건을 고쳤다.

- **만료된 `HELD`는 점유가 아니다.** 본인 점유 검사를 `existsOccupyingByMemberAndSpace`로 바꿔 조건을 슬롯 점유 조회와 같은 모양(`status IN (CONFIRMED, IN_USE) OR (status = HELD AND :now < hold_expires_at)`)으로 맞췄다. 판정 기준이 두 군데서 달라 "구간은 비었는데 내 만료된 홀드 때문에 신청이 막히는" 엇갈림이 있었다.
- **취소 후 같은 구간 재신청 허용.** `active`(WAITING이면 1, 아니면 NULL) 칸을 만들고 UNIQUE를 `(member_id, space_id, start_time, end_time, active)`로 바꿨다. 취소는 행을 지우지 않고 `CANCELLED` + `active=NULL`로 두어 이력이 남는다.
- 신규 어댑터의 Lombok을 제거했고, `COMPLETED`는 본인 점유 차단에서 제외(슬롯 점유에는 포함)로 정리했다. 테스트 19건 → 25건, 당시 전체 685 tests 통과.

### fix-2 (2026-10-15) — 검증 지적 처리

[검증 보고](../lab/notes/waitlist-outbox/step-1-review.md) / [수정 보고](../lab/notes/waitlist-outbox/step-1-fix-2.md).

- **활성 대기의 기준을 `status = WAITING AND start_time > now`로 정의**하고 3건 제한·신청 순서·`GET /waitlist/me` 세 곳에 같이 적용했다. `EXPIRED` 전이가 4단계 범위라 구간이 지난 WAITING 행이 남는데, 그 행이 제한을 영구히 차지하면 **미래의 다른 구간을 신청할 수 없었다**. 종료 시각이 아니라 시작 시각을 기준으로 삼은 이유는 4단계 알림 조건과 `ReservationTimePolicy`가 시작 시각을 보기 때문이다.
- **동시 신청을 실측**했다(위 검증 결과의 `WaitlistApplyConcurrencyTest`). fix-1이 글로만 단정했던 "UNIQUE가 한 건만 통과시킨다"가 확인됐다.
- **제약 위반 → `WAITLIST_DUPLICATE` 매핑을 활성 UNIQUE 하나로 좁혔다.** CHECK 위반까지 409 "이미 신청한 대기입니다"로 바뀌면 4단계에서 진짜 원인이 가려진다.
- **CHECK 제약이 절반만 막고 있었다**(아래 "면접·발표용 정리"의 교훈). `active = 1` 비교를 NULL 안전 비교(`<=>`)로 바꿨다.
- `SPACE_NOT_FOUND`·`SPACE_INACTIVE`, 공간 교차 3건 제한, CHECK 실동작 테스트를 더해 32건. 전체 87 클래스 697 tests 통과.

### 4단계 알림 대상 선정과 알림 조회 (2026-10-16)

보고 전문은 [lab/notes/waitlist-outbox/step-4.md](../lab/notes/waitlist-outbox/step-4.md), 검증 보고는 [step-4-review.md](../lab/notes/waitlist-outbox/step-4-review.md).

- **전체 회귀:** `./gradlew cleanTest test` → **814 tests, 실패 0, 오류 0, 건너뜀 11**(건너뜀 11건은 모두 기존 `V2SecurityRedTest`의 `@Disabled`). 4단계 직전 768 tests(3단계 시점)에서 알림 관련 테스트가 추가됐다. 취소·직접 예약 동시성 회귀 테스트(`ReservationCancelConcurrencyTest` 2, `ReservationCancelConcurrencyIntegrationTest` 5, `AdminReservationConcurrencyIntegrationTest` 13)와 `OutboxRecordIntegrationTest` 13건은 그대로 통과했다.
- **알림 대상 선정(`SlotReleasedHandlerTest`, 실제 MySQL):** 구간 전체가 비면 대기자 전원에게 알림, 일부만 비면 제외, 해제 뒤 재점유된(만료 전 `HELD`) 슬롯은 제외·만료된 `HELD`는 알림, 정지·탈퇴·`CANCELLED` 제외, 시작이 지난 항목 제외, 시작 시각이 `now`와 정확히 같으면 제외(핸들러에 고정 Clock을 주입했고, 후보 쿼리 `>`를 `>=`로 바꾸면 실패함을 확인), 본인 `CONFIRMED`·만료 전 `HELD` 겹침 제외, 다른 공간·겹치지 않는 본인 예약은 알림, 대기 구간이 해제 구간보다 넓거나 일부만 겹쳐도 전체가 비면 알림, 알림 문구에 "선착순·보장" 포함. 핸들러의 세 조건(구간 비었는지·본인 점유·회원 상태)을 각각 제거하는 변이 검사에서 해당 테스트만 정확히 실패했다.
- **갱신 유실(`WaitlistEntryLostUpdateTest`):** 래치로 순서를 고정한 인터리빙(A가 읽음 → B가 알림 시각 기록·커밋 → A가 취소 커밋)에서 `@DynamicUpdate` 없이는 `last_notified_at`이 null로 되돌아가 실패하고, 적용 뒤 보존된다. 취소된 행에는 `markNotified`가 0건 갱신한다.
- **알림 조회:** 내 알림만·최신순·페이징, 안 읽은 개수는 본인 `read_at IS NULL`만(`NotificationQueryIntegrationTest`), 토큰의 회원 id로만 조회되고 토큰이 없으면 401(`NotificationControllerTest`).
- **로컬 E2E (실제 Kafka, KRaft 단일 노드):** 대기 신청 후 점유자 취소 → outbox `PUBLISHED` → 컨슈머 → 알림 1건 → `GET /notifications`와 `unread-count`에 대기자에게만 보임(점유자는 0건). 알림 뒤 두 사람이 같은 구간을 동시 예약하면 한 명은 201(`HELD`), 한 명은 409 `RESERVATION_SLOT_CONFLICT`이고 슬롯은 한 예약 몫(30분 슬롯 2개)만 남는다. 홀드 만료 경로(`HOLD_EXPIRED`)로 풀린 슬롯을 다른 사람이 곧바로 재점유하면 대기자에게 알림이 가지 않는다. 증거와 한계는 [step-5.md](../lab/notes/waitlist-outbox/step-5.md).

## 면접·발표용 정리 (구현 후)

> 1단계~fix-2 시점의 메모다. 30초 설명·예상 질문·숫자 근거는 `00` 계획 5단계에서 채운다.

- **교훈(제약 설계)**: SQL의 CHECK는 식이 **FALSE일 때만** 막고 **NULL(unknown)이면 통과시킨다.** `(status='WAITING' AND active = 1) OR (status<>'WAITING' AND active IS NULL)`로 쓴 `chk_waitlist_entry_active`는 `(status='WAITING', active=NULL)` 행에서 식이 unknown이 되어 그대로 들어갔다 — 불변식의 절반만 막고 있었다. NULL 안전 비교(`active <=> 1`)로 바꿔 고쳤고, 이 사실은 테스트(`checkConstraint_rejectsStatusActiveMismatch`)를 쓰다가 드러났다. NULL 허용 컬럼을 제약에 쓸 때는 "FALSE가 되는가"를 직접 확인해야 한다.

### 최종 정리 (5단계, 2026-10-16)

- **30초 설명:** 원하는 시간이 이미 예약돼 있으면 "비면 알려 달라"고 대기를 걸 수 있다. 자리가 나면(취소·강제 취소·홀드 만료) 이벤트가 Outbox → Kafka로 나가고, 컨슈머가 그 시점의 DB를 다시 읽어 조건에 맞는 대기자 전원에게 알림을 저장한다. 순번을 약속하지 않고 예약은 기존 슬롯 UNIQUE로 선착순이다 — 알림은 힌트일 뿐이라 기존 예약 동시성 경로를 한 줄도 바꾸지 않았다.
- **예상 질문과 답:**
  - Q. 왜 1순위에게 자동으로 예약을 잡아 주지 않았나요?
    A. 대기자가 아닌 사람의 직접 예약을 막지 않는 한 1순위도 자리를 보장받지 못하고, 막으려면 이 프로젝트의 핵심인 예약 동시성 경로를 건드려야 한다. 그래서 알림 + 선착순으로 시작하고 순번 선점은 3차로 미뤘다.
  - Q. 알림을 받은 사람이 여럿이면 어떻게 되나요?
    A. 동시에 시도하면 한 명만 성공하고 나머지는 409를 받는다(로컬 E2E로 확인). 알림 문구에 선착순이며 보장이 없다고 명시했다.
  - Q. 취소하자마자 알림이 가는 경합은 없나요?
    A. 컨슈머가 후보를 읽은 직후 취소가 커밋되면 한 건이 나갈 수 있다. 알림은 힌트이고 최종 판정은 슬롯 UNIQUE라 허용했고, 막으려면 락이 늘어 3단계의 "락 대기 없음" 원칙을 깨기 때문이다.
  - Q. 규모가 커지면 어떻게 바꾸겠습니까?
    A. 후보 N명당 질의 약 3N+1회라 대기자가 수십 명이 넘으면 한 질의로 합치고, 알림 쓰로틀·읽음·SSE를 더하며, 다중 인스턴스에서는 relay를 분산 락이나 CDC로 바꾼다.
- **숫자·근거 한 줄:** 전체 814 tests·실패 0(건너뜀 11은 기존 `@Disabled`), 동시 신청 5스레드에서 활성 대기 1건, 같은 이벤트 4스레드 동시 처리에서 알림·처리 기록 각 1건, 본인 점유 조회는 12,000행 기준 검사 행 1,240 → 64.
