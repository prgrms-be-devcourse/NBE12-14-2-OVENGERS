# 예약 대기열과 빈자리 알림

> 상태: 초안 (구현 전). 작성: 2026-10-06. 열린 질문 결정 반영: 2026-10-06. 10/21 범위 결정 반영: 2026-10-15.

## 문제

- 원하는 시간이 이미 예약돼 있으면 사용자는 직접 계속 확인해야 한다.
- 자리가 나면 "먼저 누르는 사람"이 가져가는 경쟁이 생긴다.

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
| 취소 시 **행을 삭제**해 재신청을 허용하기 | 4칸 UNIQUE를 그대로 둘 수 있다 | 누가 언제 걸었다 취소했는지가 사라져 알림 대상 선정 논의의 근거가 남지 않는다 → `active` 칸을 UNIQUE에 넣고 행은 이력으로 보존 |

## 4. Trade-off

| 얻는 것 | 잃는 것(비용·위험) | 완화책 |
| --- | --- | --- |
| 기존 예약 경로(HOLD → 결제, 슬롯 UNIQUE)를 **한 줄도 건드리지 않는다** | 알림을 받은 N명이 동시에 시도해 N-1명이 `RESERVATION_SLOT_CONFLICT`를 받는다 | 알림 문구에 선착순이며 보장이 아님을 명시한다. 3차에서 C 또는 A로 올린다 |
| 1단계가 새 인프라(Kafka) 없이 독립적으로 완성된다 | Kafka가 늦어지면 "신청은 되는데 알림이 없는" 반쪽 기능 기간이 생긴다 | 발행을 포트(`OutboxPublisher`)로 분리해 Fake로 흐름을 먼저 완성하고 구현만 교체한다 |
| 순번 계산이 질의 하나(겹치는 선행 WAITING 수)로 끝난다 | 부분 겹침에서는 사람마다 기준 집합이 달라 **전체 순서가 아니다** | "신청 순서(참고용)"로 표기하고 그 한계를 01 문서에 적었다 |
| 중복·활성 1건을 DB가 판정한다(UNIQUE) | `active` 칸과 `status`가 어긋날 수 있다 | `chk_waitlist_entry_active` CHECK(NULL 안전 비교)로 DB에서 막고, 전이 시 `active`를 함께 비우도록 4단계 설계 문서에 선행 조건으로 적었다 |
| 활성 3건 제한을 애플리케이션에서 검사해 단순하다 | 동시 요청이 겹치면 3건을 넘길 수 있다 | 돈·슬롯이 움직이지 않아 손해가 없으므로 수용한다(01 문서 §1에 명시) |
| 점유 판정을 예약 경로의 질의로 재사용한다 | waitlist가 reservation에 의존한다 | 의존 방향을 포트(`ReservationOccupancyProvider`) + 어댑터로 뒤집어, 예약 쪽이 waitlist를 모르게 두고 기존 `OccupiedSlotProvider` 관례를 따랐다 |

## 6. 한계와 재검토 조건

- **`EXPIRED` 전이가 없다**(4단계 범위). 구간이 지난 `WAITING` 행은 남는다. 그래서 활성 판정을 `status = WAITING AND start_time > now`로 정의해 지난 행이 3건 제한·순번·내 목록에 끼지 않게 했다. 4단계에서 스윕을 넣을 때 `active`도 함께 비워야 한다.
- **알림은 보장이 아니다.** 선착순이라 오래 기다린 사람이 불리할 수 있다. 대기자 수가 늘어 "알림을 받아도 못 잡는다"는 불만이 반복되면 C(시차 알림) → A(선점권) 순으로 올린다.
- **인스턴스 1개 가정.** relay 폴링과 `@Scheduled`가 다중 인스턴스에서 중복 실행된다. 다중화하면 분산 락이나 CDC로 바꾼다(3차).
- **대기 항목은 공간·구간 단위이고 알림은 1종(`SLOT_AVAILABLE`)이다.** 우선순위(등급), 부분 매칭, SSE·메일은 범위 밖이다.
- **재검토 트리거**: ① 다중 인스턴스 배포 ② 대기 신청량이 공간당 수십 건 규모 ③ 알림 → 예약 성공률이 낮게 측정될 때 ④ 자동 선점권을 3차에서 구현할 때.

## 10/21 범위 (2026-10-15)

이번 구현 범위(전원 알림 + 선착순, 신청 규칙, 본인 점유자 차단, 구현 순서)는 [plans/waitlist-outbox](../plans/waitlist-outbox/00-waitlist-outbox-plan.md)에 둔다. 이 문서의 선택안 3·4·6번과 그에 딸린 결정은 자동 선점권 모델이라 3차로 이월했다. 정지 회원은 10/21 범위에서 "알림만 건너뛰고 항목 유지", 알림 후 항목 상태는 WAITING 유지로 정했다.

## 열린 질문

- (해소, 2026-10-15) 건너뛴 정지 회원의 대기 항목은 대기로 유지한다.

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

## 면접·발표용 정리 (구현 후)

> 1단계~fix-2 시점의 메모다. 30초 설명·예상 질문·숫자 근거는 `00` 계획 5단계에서 채운다.

- **교훈(제약 설계)**: SQL의 CHECK는 식이 **FALSE일 때만** 막고 **NULL(unknown)이면 통과시킨다.** `(status='WAITING' AND active = 1) OR (status<>'WAITING' AND active IS NULL)`로 쓴 `chk_waitlist_entry_active`는 `(status='WAITING', active=NULL)` 행에서 식이 unknown이 되어 그대로 들어갔다 — 불변식의 절반만 막고 있었다. NULL 안전 비교(`active <=> 1`)로 바꿔 고쳤고, 이 사실은 테스트(`checkConstraint_rejectsStatusActiveMismatch`)를 쓰다가 드러났다. NULL 허용 컬럼을 제약에 쓸 때는 "FALSE가 되는가"를 직접 확인해야 한다.
