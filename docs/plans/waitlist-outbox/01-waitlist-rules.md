# 대기 신청 규칙 (10/21)

> 작성: 2026-10-15. [계획](00-waitlist-outbox-plan.md)의 1단계 범위다. 알림 대상 선정은 [02 Outbox·Kafka 설계](02-outbox-kafka-design.md)의 컨슈머 절을 따른다.

## 1. 신청 규칙

- 구간은 기존 예약과 같은 규칙(30분 정렬, 시작이 미래). 구간의 슬롯이 **하나라도 점유 중일 때만** 신청할 수 있다. 전부 비어 있으면 `WAITLIST_NOT_NEEDED`로 거절한다(그냥 예약하면 되므로). 검사와 INSERT 사이 경합은 수용한다.
- 회원은 ACTIVE여야 하고, 취소는 본인 소유만 가능하다(`FORBIDDEN_NOT_OWNER`).
- **본인 점유자는 같은 공간에 대기를 걸 수 없다.**
  - 신청 시점: 같은 회원의 **만료되지 않은** `HELD`(결제 대기)·`CONFIRMED`(결제 완료)·`IN_USE` 예약이 같은 공간에서 신청 구간과 겹치면 `WAITLIST_ALREADY_HOLDING`으로 거절한다. 자기 슬롯을 기다리는 것은 의미가 없다. "같은 공간"은 시간이 겹치는 경우로 해석한다(다른 시간 예약까지 막지 않는다). *(2026-10-15 fix-1: 점유의 기준을 예약 경로와 한 군데로 맞췄다 — 만료된 HELD(`hold_expires_at <= now`)는 점유가 아니므로 본인 점유 검사에서도 제외한다. 기준이 두 군데서 달라지면 "구간이 비었다"는 판단과 "내가 점유 중이다"는 판단이 서로 엇갈린다.)*
  - 대상 상태: 본인 점유 차단은 **만료되지 않은 `HELD` / `CONFIRMED` / `IN_USE`**이며 `COMPLETED`는 제외한다. 체크아웃은 슬롯을 지우지 않으므로(core-domain-decisions 3-4) 끝난 예약의 슬롯은 **점유 판정에는 포함**되지만, 이미 쓰고 나온 자리는 "자기 자리를 기다리는" 경우가 아니라 대기를 막을 이유가 없다(조기 체크아웃이면 종료 시각이 아직 미래인 `COMPLETED`가 생긴다). 즉 두 판정의 상태 목록이 다른 것은 의도된 차이다.
  - 알림 시점: 컨슈머가 **같은 질의**를 다시 호출해 해당자를 건너뛴다(만료되지 않은 HELD·CONFIRMED·IN_USE 기준). 신청 뒤에 본인이 다른 경로로 예약을 잡을 수 있으므로 신청 시점 검사는 스냅샷일 뿐이고, **최종 방어선은 알림 시점 검사**다.
  - 인덱스: `reservation`에 `(member_id, space_id, status, start_time)`이 필요하다. 현재는 `start_time`, `(space_id, start_time)` 인덱스만 있으므로 구현 단계에서 `EXPLAIN`으로 확인하고 필요하면 추가한다. *(2026-10-15: V17에서 추가했다. 12,000행 기준 EXPLAIN에서 검사 행 1,240 → 64.)*
    - *(2026-10-15 3단계 fix-2 — `key_len`·`Extra` 포함 재측정)* MySQL 8.4, 별도 스키마에 `reservation` 12,600행(편중 케이스: member 1·space 1에 660건)을 만들고 `existsOccupyingByMemberAndSpace`의 WHERE를 그대로 `COUNT(*) > 0`으로 실행했다. 두 인덱스를 `FORCE INDEX`로 비교한 결과다. 데이터는 합성이라 절대값이 아니라 **인덱스 간 상대 비교**로 읽는다.

      | 케이스 | 인덱스 | type | key_len | EXPLAIN rows | 실제 읽은 행(`EXPLAIN ANALYZE`) | Extra |
      | --- | --- | --- | --- | --- | --- | --- |
      | 편중(660건) | `(member_id, space_id, status, start_time)` | range | 103 | 71 | 70 | Using index condition; Using where |
      | 편중(660건) | `(member_id, space_id, start_time)` | range | 21 | 256 | 256 | Using index condition; Using where |
      | 일반(회원·공간당 약 6건) | `(member_id, space_id, status, start_time)` | range | 103 | 13 | 12 | Using index condition; Using where |
      | 일반(회원·공간당 약 6건) | `(member_id, space_id, start_time)` | range | 21 | 17 | 17 | Using index condition; Using where |

      - 판정: **4칸 인덱스를 유지한다.** `key_len` 103 = 8(member_id) + 8(space_id) + 82(status, `VARCHAR(20)` utf8mb4: 20×4+2) + 5(start_time)이라 네 칸이 모두 범위 축소에 쓰인다. 우려했던 "`status`가 동등 조건이 아니라 4번째 칸이 못 쓰인다"는 일어나지 않았다 — 옵티마이저가 `IN (…) OR (status='HELD' …)`를 상태별 다중 범위(`status='CONFIRMED'`, `'IN_USE'`, `'HELD'` 각각에 `start_time < …`)로 풀어 준다. 힌트 없이 실행해도 4칸 인덱스를 고른다.
      - 이유: 3칸 인덱스는 `start_time < :endTime` 하나만 범위로 써서, 본인의 CANCELLED·COMPLETED 같은 점유 아닌 과거 예약까지 읽고 `Using where`로 걸러낸다(편중 케이스에서 256행 vs 70행). 일반 케이스는 차이가 작다(17 vs 12).
      - 비용: 4칸 인덱스는 항목 폭이 103바이트로 3칸(21바이트)의 약 5배라 쓰기·저장 비용이 더 든다. 예약 쓰기는 대기 신청 경로가 아니라 기존 예약 경로이므로, 이 비용은 예약 INSERT/UPDATE마다 붙는다는 점을 trade-off로 남긴다.
      - 한계: 합성 데이터이고 상태 분포·시간 분포를 가정했다. 실제 분포에서 상태 컬럼이 거의 점유 상태뿐이면 `status` 칸의 이득이 줄어 3칸이 충분할 수 있다(재검토 조건).
- 같은 신청 중복은 UNIQUE(`member_id, space_id, start_time, end_time, active`)로 막는다(`WAITLIST_DUPLICATE`). `active`는 WAITING일 때만 1이고 그 밖의 상태에서는 NULL이며, MySQL이 UNIQUE에서 NULL을 서로 다른 값으로 보므로 "활성 신청은 구간당 1건"을 강제하면서 취소된 이력 행은 여러 개 남는다(`door_access_token`의 `active_reservation_id` + UNIQUE와 같은 방식). 따라서 **취소한 뒤 같은 구간을 다시 신청할 수 있고**, 재신청은 새 행이므로 신청 순서는 맨 뒤가 된다. 활성(WAITING) 최대 3건은 애플리케이션 검사이며 동시 요청 시 초과를 수용한다(`WAITLIST_LIMIT_EXCEEDED`). *(2026-10-15 fix-1: UNIQUE에 status 성격의 칸이 없어 취소 후 재신청이 막혔던 것을 고쳤다. 취소 시 행을 지우지 않는 이유는 누가 언제 걸었다 취소했는지가 알림 대상 논의의 근거로 남아야 하기 때문이다.)*
- **"활성 대기"의 기준은 `status = WAITING AND start_time > now`이고, 세 곳(3건 제한·신청 순서·`GET /waitlist/me`)에서 모두 같다.** 종료 시각이 아니라 **시작 시각** 기준인 이유는 4단계 알림 조건과 `ReservationTimePolicy`가 모두 시작 시각을 보기 때문이다. `EXPIRED` 전이는 4단계 범위라 구간이 지난 WAITING 행이 남는데, 이 기준이 없으면 지난 대기 3건이 활성 제한을 영구히 차지해 **미래의 다른 구간을 신청할 수 없다**. *(2026-10-15 fix-2, step-1-review.md H1)*
  - 이 기준의 결과: **진행 중 구간(이미 시작된 구간)의 대기는 포기한다.** 시작이 지난 구간은 `ReservationTimePolicy`상 신규 예약이 불가능해 알림을 줘도 예약할 수 없다. 체크아웃은 슬롯을 지우지 않으므로 조기 체크아웃에서는 자리가 나지 않고, 진행 중 슬롯이 풀리는 것은 관리자 강제 취소뿐이다.
- 4단계에서 `WAITING` → `EXPIRED`로 전이할 때는 **`active`도 NULL로 함께 비워야 한다.** `chk_waitlist_entry_active` CHECK 때문에 `status`만 바꾸는 UPDATE는 실패한다.
- 신청 순서: 같은 space에서 구간이 **겹치는** 활성 WAITING 중 나보다 `id`가 작은 건수 + 1. 안내용이다. 구간이 **부분만** 겹치면(A 14:00–15:00, B 14:30–15:30, C 14:00–14:30) 사람마다 기준 집합이 달라 **전체 순서가 아니다** — "내 구간과 겹치는 사람 중 몇 번째"라는 뜻으로만 읽는다.

## 2. 데이터와 API

모두 FK를 걸지 않는다(Member/Space 잠금 순서 데드락 이력).

| 테이블 | 컬럼 | 제약·인덱스 |
| --- | --- | --- |
| `waitlist_entry` | id, member_id, space_id, start_time, end_time, status(WAITING/CANCELLED/EXPIRED), active(TINYINT NULL — WAITING이면 1, 아니면 NULL), last_notified_at, created_at | UNIQUE(member_id, space_id, start_time, end_time, active), CHECK(status='WAITING' <=> active=1 — 둘이 어긋난 행을 DB에서 막는다), INDEX(space_id, status, start_time), INDEX(member_id, status) |
| `notification` | id, member_id, waitlist_entry_id, event_id, message, read_at, created_at *(2026-10-16 구현 기준 정정 — 초안의 `type`·`ref_entry_id`는 두지 않았다. 종류가 하나뿐이고 대기 항목을 `waitlist_entry_id`로 직접 가리킨다. 결정: [waitlist 결정 문서](../../decisions/waitlist.md))* | UNIQUE(event_id, waitlist_entry_id), INDEX(member_id, id), INDEX(member_id, read_at) |

API: `POST /waitlist`, `DELETE /waitlist/{id}`, `GET /waitlist/me`, `GET /notifications`, `GET /notifications/unread-count`. *(2026-10-16: `PATCH /notifications/{id}/read`는 구현하지 않았다 — 계획이 읽음 API를 자르기 대상으로 정했다. 3차.)* Flyway 번호는 브랜치 분기 시점의 다음 번호를 쓰고, 천종원(계정 V1~V3)이 먼저 머지하면 재번호한다.
