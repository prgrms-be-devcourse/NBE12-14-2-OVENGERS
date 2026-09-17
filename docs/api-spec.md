# Slot Key API 명세서

> claude.ai 프로젝트 문서 `claude/api-명세서.md` (v0.1, 2026-09-09, 기획서 확정본 기준) 동기화본. 구현 중 세부 규칙이 바뀌면 이 문서도 함께 갱신한다.
> **2026-09-15 갱신**: `docs/core-domain-decisions.md`(확정본) 반영 — 예약 생성이 1단계(즉시 결제)에서 2단계(HOLD → 결제 확인)로 변경, `payment` 삭제·`credit_transaction` 도입, 연장/체크아웃/크레딧 지급 엔드포인트 신규, 도어 토큰 발급·체크인 시간 규칙 변경. 충돌 시 `core-domain-decisions.md`가 우선한다.
> **2026-09-17 갱신 (docs/api)**: 실제 컨트롤러 코드 기준으로 동기화 — Base URL `/api/v1`, 취소 API `POST .../cancel`, 요청/응답 필드, 관리자 예약 목록 필터 미지원, 미구현 API 표시. **프론트는 이 문서를 기준으로 한다.** (claude.ai 프로젝트 문서 `claude/api-명세서.md`는 구버전 — 참고 금지)

## 공통 사항

- Base URL: `/api/v1` (예: `/spaces` → 실제로는 `/api/v1/spaces`)
  - 출입(§7) API도 `/api/v1`로 통일됨(2026-09-17, 기존 `/api`).
- 인증: Access Token(JWT, `Authorization: Bearer`), Refresh Token(임의 문자열, 재발급 전용 엔드포인트에서만 사용, 쿠키 전달 시 HttpOnly/Secure/SameSite)
- JWT 클레임은 `sub`(회원 식별자), `exp`만 포함. 역할·상태는 넣지 않고 **모든 보호 API가 요청 시점에 DB에서 재조회**한다.
- 인증 불필요 API: 회원가입, 로그인, 토큰 재발급, 공간 목록/상세 조회, 슬롯 가용성 조회.
- 공통 응답: `{ status, code, message, data }` (성공 SUCCESS/OK, 실패 시 data는 항상 null)
- 페이지네이션: `page`(0-base, 기본 0), `size`(기본 20, 최대 100) → `{ content, page, size, totalElements, totalPages }`
- 시간: ISO-8601, `+09:00` 기준. 예약 시간은 **30분의 배수**만 허용. `Clock` 주입으로 서버 시각 판단.
- `Idempotency-Key`(UUID) 헤더: **실제로 돈이 움직이는 `POST /reservations/{id}/pay`에만 필수.** `POST /reservations`(HOLD 생성)는 결제가 없으므로 대상 아님. 동일 키 재요청 시 최초 처리 결과를 그대로 반환(재처리 금지). *(2026-09-15 정정 — 기존엔 "예약 생성"에 붙어 있었음)*

### HTTP 상태 코드 기준

| 코드 | 의미 | 예 |
| --- | --- | --- |
| 200 | 조회/처리 성공 | 목록·상세, 취소 성공 |
| 201 | 생성 성공 | 예약(HOLD) 생성, 공간 등록, 도어 토큰 발급 |
| 400 | 요청 형식 오류 | 필수 필드 누락, 형식 위반 |
| 401 | 인증 실패 | 토큰 없음/만료/위조 |
| 403 | 인가 실패 | 역할·소유권 불충분 |
| 404 | 자원 없음 | 존재하지 않는 spaceId/reservationId |
| 409 | 상태 충돌 | 슬롯 중복, 이미 취소/만료된 예약에 대한 요청, 홀드 만료 후 결제 시도, `space.version` 불일치(가격 확인 실패) |
| 422 | 정책 위반 | 크레딧 잔액 부족, 운영시간 외, 30분 배수 위반, 자기 자신에게 크레딧 지급 |

## 2. 인증 (Auth)

| API | 메서드/경로 | 인증 | 비고 |
| --- | --- | --- | --- |
| 회원가입 | `POST /auth/signup` | 불필요 | `role`은 요청으로 받지 않음, 서버가 항상 MEMBER로 생성. 가입과 동일 트랜잭션에서 `SIGNUP_GRANT` 크레딧 지급(`app.credit.signup-grant`). 오류: VALIDATION_FAILED(400), EMAIL_ALREADY_EXISTS(409) |
| 로그인 | `POST /auth/login` | 불필요 | 오류: INVALID_CREDENTIALS(401), MEMBER_SUSPENDED(403) |
| 토큰 재발급 | `POST /auth/refresh` | 불필요(refreshToken 바디) | 기존 토큰 즉시 폐기 후 회전. 오류: INVALID_REFRESH_TOKEN(401) |
| 로그아웃 | `POST /auth/logout` | 필요 | 해당 Refresh Token만 폐기, Access Token은 만료까지 유효 |

## 3. 회원 (Member)

- `GET /members/me` (인증 필요): 본인 정보 조회. `balance`(크레딧 잔액) 포함
> ⚠️ **2026-09-17 기준 아래 관리자 회원 API 3개(suspend/restore/credits)는 컨트롤러 미구현.** 현재 존재하는 회원 API는 `GET /members/me`뿐이다.

- `PATCH /admin/members/{memberId}/suspend` (PLATFORM_ADMIN): `reason` 1~500자 필수, 대상은 MEMBER만, audit_log 기록. 오류: FORBIDDEN_ROLE(403), TARGET_IS_ADMIN(422), MEMBER_NOT_FOUND(404)
- `PATCH /admin/members/{memberId}/restore` (PLATFORM_ADMIN): suspend와 동일 구조, status → ACTIVE
- `POST /admin/members/{memberId}/credits` (PLATFORM_ADMIN): 크레딧 지급(`ADMIN_GRANT`). `amount`(양수), `reason` 1~500자 필수. **자기 자신에게는 지급 불가**(`actor_id != member_id`). **지급만 가능, 회수 없음.** `credit_transaction`과 `audit_log` 양쪽에 기록. 오류: VALIDATION_FAILED(400), FORBIDDEN_ROLE(403), SELF_GRANT_NOT_ALLOWED(422), MEMBER_NOT_FOUND(404)

## 4. 공간 (Space)

- `GET /spaces` (인증 불필요): `page`, `size`, `keyword`. `status=ACTIVE`인 공간만 기본 노출
- `GET /spaces/{spaceId}` (인증 불필요): 상세 (+ description, `version`). 오류: SPACE_NOT_FOUND(404)
- `GET /spaces/{spaceId}/slots?date=YYYY-MM-DD`: 운영시간을 30분 단위로 쪼갠 예약 가능 여부. **참고용 스냅샷**(실제 확정 여부는 슬롯 INSERT 시점의 UNIQUE 제약으로만 판정 — 사전 조회는 화면 표시용일 뿐 규칙이 아니다)
- `POST /admin/spaces` (PLATFORM_ADMIN): `pricePerSlot`은 100원 단위 양수만. 등록자 ID·생성 시각은 서버가 설정. audit_log 기록. 오류: VALIDATION_FAILED(400), FORBIDDEN_ROLE(403)
- `PATCH /admin/spaces/{spaceId}` (PLATFORM_ADMIN): 부분 수정 + status(ACTIVE/INACTIVE). 가격 변경 시 `version` 증가(낙관적 비교용) — 이미 확정된 예약의 스냅샷/총액에는 영향 없음. INACTIVE로 바꿔도 기존 예약 유지, 신규 예약만 차단. 오류: SPACE_NOT_FOUND(404), FORBIDDEN_ROLE(403), VALIDATION_FAILED(400)

## 5. 예약 · 크레딧 (Reservation & Credit)

> **프론트 예약 흐름 요약**: ① `POST /reservations`로 HOLD 생성(201) → 응답의 `reservationId`, `spaceVersion`, `holdExpiresAt` 보관 → ② 10분 안에 `POST /reservations/{id}/pay`(헤더 `Idempotency-Key`, 바디 `spaceVersion`)로 확정. 한 번의 호출로 결제까지 끝나지 않는다. 응답에 `payment` 객체는 없다.

#### 공통 응답 `ReservationResponse` (HOLD/pay/cancel/extend/check-out/목록 원소)

```json
{
  "reservationId": 1, "spaceId": 3,
  "startTime": "2026-09-20T14:00:00", "endTime": "2026-09-20T15:00:00",
  "status": "HELD", "pricePerSlotSnapshot": 5000, "totalAmount": 10000,
  "holdExpiresAt": "2026-09-17T13:10:00",
  "checkedInAt": null, "checkedOutAt": null, "cancelledAt": null,
  "spaceVersion": 2,
  "refundAmount": null, "penaltyAmount": null,
  "createdAt": "2026-09-17T13:00:00"
}
```

- `spaceVersion`: **HOLD 생성 응답에서만** 값이 있음(그 외 null). pay 요청 때 그대로 되돌려 보낸다.
- `refundAmount`/`penaltyAmount`: **취소 응답에서만** 값이 있음(그 외 null).
- 시간 필드는 오프셋 없는 `LocalDateTime` 문자열(서버 기준 KST).

### 5-1. 예약 생성(HOLD) — `POST /reservations`

요청 바디:

```json
{ "spaceId": 3, "date": "2026-09-20", "startTime": "14:00", "endTime": "15:00" }
```

응답: **201**, `data` = `ReservationResponse`(`status: "HELD"`).

`Idempotency-Key` 불필요(이 단계는 결제가 없음). 처리 순서:

1. 회원 상태(ACTIVE) 확인
2. 규칙 검사(§9 규칙 엔진): 운영시간 내, 30분 배수, 단일 요금 구간, 과거 시간 아님, 본인 시간대 중복 없음, 최대 예약 시간·동시 보유 한도 이내, (사전 안내용) 크레딧 잔액 충분. **슬롯 가용성은 이 목록에 없음** — 사전 조회는 화면 표시용일 뿐 최종 판정이 아니다.
3. 대상 슬롯의 만료된 HELD를 조건부 UPDATE로 EXPIRED 전이 + 슬롯 삭제(배치를 기다리지 않는 즉시 정리)
4. `price_per_slot × 슬롯 수`로 `totalAmount` 계산
5. 슬롯 유니크 INSERT 시도 → 성공 시 `HOLD` 생성(`hold_expires_at` = now + 10분) + 최초 상태 이력 저장. 응답에 `totalAmount`, `space.version`, `hold_expires_at` 포함

오류: VALIDATION_FAILED(400), MEMBER_NOT_ACTIVE(403), SPACE_NOT_FOUND(404), SPACE_INACTIVE(422), RESERVATION_SLOT_CONFLICT(409, 슬롯 INSERT의 UNIQUE 위반이 유일한 진실)

### 5-2. 결제 확인 및 확정 — `POST /reservations/{id}/pay`

요청: 헤더 `Idempotency-Key: <UUID>`(누락 시 400), 바디 `{ "spaceVersion": 2 }`. 응답: **200**, `data` = `ReservationResponse`(`status: "CONFIRMED"`).

헤더 `Idempotency-Key` 필수(돈이 움직이는 지점). 아래 전 과정을 **하나의 트랜잭션**으로 처리하며, 어느 단계에서든 실패하면 전체 롤백한다(예약은 `HOLD`로 남아 만료 전까지 재시도 가능):

1. 예약자 본인 확인
2. 요청 바디의 `space.version`을 현재 값과 비교(값이 아니라 **버전**으로 비교 — ABA 문제 방지). 다르면 즉시 거절(409), 이후 단계 진행하지 않음
3. 크레딧 조건부 차감 `WHERE balance >= :amount`(서버가 직접 읽은 금액으로 차감, 클라이언트가 보낸 금액은 신뢰하지 않음) → 영향 행 0이면 422(`INSUFFICIENT_BALANCE`)
4. `WHERE id=:id AND status='HELD' AND :now < hold_expires_at` 조건부 UPDATE로 `CONFIRMED` 전이 → 영향 행 0이면 409(만료 또는 이미 처리됨; 3에서 차감한 크레딧도 함께 롤백)
5. `credit_transaction`(`RESERVATION_CHARGE`) 기록 + 상태 이력 저장

오류: RESERVATION_NOT_FOUND(404), FORBIDDEN_NOT_OWNER(403), SPACE_VERSION_MISMATCH(409), INSUFFICIENT_BALANCE(422), RESERVATION_STATE_CONFLICT(409, 만료/이미결제/취소됨)

### 5-3. 조회

- `GET /reservations?status=&page=&size=` (인증 필요, 본인 예약만). 응답 `data`는 Spring `Page<ReservationResponse>` 직렬화 형태(`content`, `totalElements`, `totalPages`, `number`, `size` …). `status` 생략 시 전체. `status`는 `HELD`/`EXPIRED`/`CONFIRMED`/`IN_USE`/`COMPLETED`/`CANCELLED`/`NO_SHOW` 중 하나
- `GET /reservations/{reservationId}` (본인 소유만). 응답 `ReservationDetailResponse` = `ReservationResponse`에서 `spaceVersion`/`refundAmount`/`penaltyAmount`를 뺀 필드 + `statusHistory: [{ fromStatus, toStatus, reason, changedAt }]`. 오류: RESERVATION_NOT_FOUND(404), FORBIDDEN_NOT_OWNER(403)

### 5-4. 취소 — `POST /reservations/{reservationId}/cancel`

> ⚠️ `DELETE /reservations/{id}`가 아니다(2026-09-17 정정). 바디 없음. 응답: **200**, `data` = `ReservationResponse`(`status: "CANCELLED"`, `refundAmount`, `penaltyAmount` 포함).

예약자 본인만. 시작 1시간 전까지 100% 환불, 1시간 전~시작 전 50% 환불, 시작 이후는 취소 불가(체크아웃으로만 종료).

1. 조건부 UPDATE: `WHERE id=:id AND status='CONFIRMED' AND :now < start_time` → `CANCELLED`(`cancelled_at`=now). 영향 행 0이면 409
2. (같은 트랜잭션) 슬롯 삭제 + 활성 토큰 revoke + 크레딧 환급(`REFUND` 전액, 필요시 `PENALTY` 위약금을 **두 줄로 분리 기록**) + 상태 이력 저장

오류: FORBIDDEN_NOT_OWNER(403), RESERVATION_NOT_FOUND(404), RESERVATION_STATE_CONFLICT(409)

> 취소와 환불이 같은 DB의 한 트랜잭션이므로 "취소는 됐는데 환불은 실패"하는 중간 상태가 구조적으로 없다. 별도의 환불 재처리 큐는 필요 없다.

### 5-5. 연장 — `POST /reservations/{id}/extend`

요청 바디: `{ "expectedEndTime": "2026-09-20T15:00:00", "newEndTime": "2026-09-20T16:00:00" }` (`expectedEndTime` = 클라이언트가 알고 있는 현재 종료 시각). 응답: **200**, `ReservationResponse`.

예약자 본인만. `now < end_time`인 경우만 가능(끝난 예약을 되살리는 것은 연장이 아니라 새 예약). 단일 트랜잭션: `end_time` 낙관적 검사(`WHERE id=:id AND end_time=:expectedEnd`) → 추가 슬롯 INSERT(UNIQUE) → 추가 금액을 **원 예약의 `price_per_slot_snapshot`** 기준으로 크레딧 차감 → `reservation.end_time` UPDATE → 상태 이력 저장. 남의 점유가 `HELD`인지 `CONFIRMED`인지는 구분하지 않는다(만료된 HELD만 예외).

오류: FORBIDDEN_NOT_OWNER(403), RESERVATION_NOT_FOUND(404), RESERVATION_STATE_CONFLICT(409, 연장 슬롯 일부/전부 점유 — 응답에 가능한 최대 종료 시각 힌트 포함), INSUFFICIENT_BALANCE(422)

> 실패해도 원 예약은 무손상(기존 슬롯을 건드리지 않음).

### 5-6. 체크아웃 — `POST /reservations/{id}/check-out`

바디 없음. 응답: **200**, `ReservationResponse`(`status: "COMPLETED"`).

예약자 본인만. `WHERE id=:id AND status='IN_USE'` 조건부 UPDATE → `COMPLETED`(`checked_out_at`=now). 같은 트랜잭션에서 활성 토큰 revoke + 상태 이력 저장. 슬롯 반환 없음, 환불 없음. **되돌릴 수 없다**(UI에 확인 다이얼로그).

> 체크인에는 별도 엔드포인트가 없다 — 최초 체크인은 §7 도어 토큰 검증의 부수 효과로 `CONFIRMED → IN_USE` 전이가 일어난다.

오류: FORBIDDEN_NOT_OWNER(403), RESERVATION_NOT_FOUND(404), RESERVATION_STATE_CONFLICT(409)

## 6. [관리자] 예약 관리

- `GET /admin/reservations?page=&size=` (PLATFORM_ADMIN): 취소 예약도 목록 포함. 원소 `AdminReservationResponse` = `{ reservationId, memberId, memberEmail, spaceId, spaceName, startTime, endTime, status, totalAmount, createdAt }`. ⚠️ **`date`/`spaceId`/`status` 필터는 현재 미구현**(`AdminReservationSearchCondition`이 빈 클래스) — 프론트는 필터 UI를 비활성화하거나 구현 후 연결
- `GET /admin/reservations/{reservationId}` (PLATFORM_ADMIN): 목록 필드 + `pricePerSlotSnapshot`, `cancelledAt`, `checkedInAt`, `checkedOutAt`, `statusHistory[]`, `accessLogs: [{ accessLogId, attemptedAt, result, reasonCode }]`
- `POST /admin/reservations/{reservationId}/force-cancel` (PLATFORM_ADMIN): 바디 `{ "reason": "..." }` 1~500자 필수, 응답 `AdminReservationResponse`. 처리 로직은 5-4와 동일 + audit_log 기록. **관리자도 이 API 외의 경로로 타인 예약을 취소하거나 도어 토큰을 발급받을 수 없다** (핵심 차별점)

## 7. 출입 (Door Access)

> 현재 구현 경로(접두사 `/api/v1`): `POST /reservations/{id}/door-token`, `PATCH /reservations/{id}/access-token/revoke`, `GET /reservations/{id}/access-logs`, `POST /door-access/verify`. **4개 모두 로그인 필요**(verify도 서버가 로그인 회원이 예약자 본인인지 확인). revoke·access-logs는 이 문서에 세부 규칙 미기재 — 담당자 확인 필요.

- `POST /reservations/{reservationId}/door-token` (예약자 본인만): **발급에는 시간 제한이 없다** — `CONFIRMED`이고 `now < end_time`이면 예약 확정 직후부터 언제든 발급 가능(발급은 입장 권한이 아니라 신분증을 받는 것일 뿐, `start` 전엔 문이 열리지 않는다). 기존 활성 토큰은 먼저 폐기 후 재발급. 오류: FORBIDDEN_NOT_OWNER(403, 관리자 포함), RESERVATION_NOT_FOUND(404), RESERVATION_NOT_CONFIRMED(422)
- `POST /door-access/verify`: `{ spaceId, token }`. 검증 순서: ① 해시로 활성 토큰 조회(폐기 토큰은 즉시 거절) ② 예약 상태(`CONFIRMED` 또는 `IN_USE`) 확인, 요청 공간=예약 공간 확인 ③ 시간대 확인 — **최초 체크인: `[start_time, start_time + 15분]`(앞 여유 0분, 시작 시각 정각 허용)**, **재입장: `(checked_in_at, end_time)`(종료 시각 정각은 거절)** ④ 최초 체크인 성공 시 `CONFIRMED → IN_USE` 전이가 부수 효과로 일어남 ⑤ 성공/실패 모두 `door_access_log`에 기록. 거절도 200 + `result: DENY`로 응답. `reasonCode`: TOKEN_NOT_FOUND, TOKEN_REVOKED, RESERVATION_NOT_ACTIVE(취소/완료/노쇼), OUTSIDE_ALLOWED_TIME, SPACE_MISMATCH

> **2026-09-15 정정** (`core-domain-decisions.md` §8): 기존 "발급 30분 전부터, 최초 체크인 시작 전후 30분" 규칙을 대체한다. 발급 시간 제한을 없애고, 최초 체크인은 앞 여유 0분·뒤 15분(`[start, start+15m]`)으로 변경. 15분 내 미체크인은 배치가 `NO_SHOW`로 전이시키며 슬롯은 반환되지만 환불은 없다.

## Enum

| 항목 | 값 |
| --- | --- |
| `member.role` | USER, ADMIN (문서 본문의 MEMBER/PLATFORM_ADMIN 표기는 각각 USER/ADMIN을 뜻함) |
| `member.status` | ACTIVE, SUSPENDED |
| `space.status` | ACTIVE, INACTIVE |
| `reservation.status` | HELD, EXPIRED, CONFIRMED, IN_USE, COMPLETED, CANCELLED, NO_SHOW |
| `credit_transaction.type` | SIGNUP_GRANT, ADMIN_GRANT, RESERVATION_CHARGE, REFUND, PENALTY |
| `door_access_log.result` | ALLOW, DENY |

> ~~`payment.status`~~ 는 `payment` 테이블 삭제와 함께 제거됨(§1-1, §11). 결제 결과는 `credit_transaction`으로 표현한다.

## 팀 확인 필요 사항 (구현 착수 전 합의 권장)

1. **감사 로그 조회 API**: `GET /admin/audit-logs`는 DoD에 명시되어 있지 않음. 필요 시 추가 정의(우선순위 낮음, MVP 3개 기능 범위 밖).
2. **노쇼 환불률**: 현재 확정값은 0%이며 강사 피드백에 따라 재검토 중(`core-domain-decisions.md` §13). 바뀌면 이 문서와 `credit_transaction.type` 처리 로직도 함께 갱신.

> 2026-09-14 확정: 도어 토큰 발급 시작 시점은 예약 시작 30분 전부터(기획서 4-3 시나리오 "14:30"은 오타 — 14:00 시작 기준 정정값은 13:30으로 규칙과 일치). 최초 체크인 허용 구간은 시작 전후 30분(기존 15분에서 변경)으로 확정.
> 2026-09-15 갱신: 위 2026-09-14 확정 내용은 `core-domain-decisions.md` §8로 다시 대체되었다(발급 시간 제한 없음, 체크인은 `[start, start+15분]`). 이 문서의 "팀 확인 필요 사항"에는 더 이상 해당하지 않으며, §7을 최신 기준으로 본다.
