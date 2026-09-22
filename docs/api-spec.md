# Slot Key API 명세서

> claude.ai 프로젝트 문서 `claude/api-명세서.md` (v0.1, 2026-09-09, 기획서 확정본 기준) 동기화본. 구현 중 세부 규칙이 바뀌면 이 문서도 함께 갱신한다.
> **2026-09-15 갱신**: `docs/core-domain-decisions.md`(확정본) 반영 — 예약 생성이 1단계(즉시 결제)에서 2단계(HOLD → 결제 확인)로 변경, `payment` 삭제·`credit_transaction` 도입, 연장/체크아웃/크레딧 지급 엔드포인트 신규, 도어 토큰 발급·체크인 시간 규칙 변경. 충돌 시 `core-domain-decisions.md`가 우선한다.
> **2026-09-17 갱신 (docs/api)**: 실제 컨트롤러 코드 기준으로 동기화 — Base URL `/api/v1`, 취소 API `POST .../cancel`, 요청/응답 필드, 관리자 예약 목록 필터 미지원, 미구현 API 표시. **프론트는 이 문서를 기준으로 한다.** (claude.ai 프로젝트 문서 `claude/api-명세서.md`는 구버전 — 참고 금지)

## 공통 사항

- Base URL: `/api/v1` (예: `/spaces` → 실제로는 `/api/v1/spaces`)
  - 출입(§7) API도 `/api/v1`로 통일됨(2026-09-17, 기존 `/api`).
- 인증: Access Token(JWT, `Authorization: Bearer`), Refresh Token(DB 저장, `POST /auth/refresh`, `POST /auth/logout`에서 HttpOnly 쿠키로 전달)
- JWT 페이로드는 `id`, `email`, `role`, 만료 시각(`exp`)을 포함한다. 보호 API 요청 시 서명과 만료 시각을 검증하여 인증하며, **매 요청마다 회원 DB를 재조회하지 않는다**. 회원이 정지되어도 기존 Access Token은 만료 전까지 유효하지만, 토큰 재발급(`POST /auth/refresh`) 시 DB 조회를 거쳐 `ACCOUNT_INACTIVE`(403)로 차단된다.
- 인증 불필요 API: 회원가입, 로그인, 토큰 재발급, 공간 목록/상세 조회, 슬롯 가용성 조회.
- 공통 응답: `{ status, code, message, data }` (성공 SUCCESS/OK, 실패 시 data는 항상 null)
- 페이지네이션: `page`(0-base, 기본 0), `size`(기본 20, 최대 100) → `{ content, page, size, totalElements, totalPages }`
- 시간: ISO-8601, `+09:00` 기준. 예약 시간은 **30분의 배수**만 허용. `Clock` 주입으로 서버 시각 판단.
- `Idempotency-Key`(UUID) 헤더: **실제로 돈이 움직이는 `POST /reservations/{id}/pay`에만 필수.** `POST /reservations`(HOLD 생성)는 결제가 없으므로 대상 아님. 동일 키 재요청 시 최초 처리 결과를 그대로 반환(재처리 금지). *(2026-09-15 정정 — 기존엔 "예약 생성"에 붙어 있었음)*
- 완전히 동시에 도착한 요청(진짜 레이스)은 예외다: 캐시 조회와 저장이 원자적이지 않아, 동시에 들어온 요청 중 일부가 캐시를 못 찾고 실제 처리 로직까지 진입할 수 있다. 이 경우 진 쪽은 정상적인 `409 RESERVATION_STATE_CONFLICT`를 받으며, 같은 키로 재시도하면 그때는 저장된 최초 응답을 그대로 돌려받는다. 크레딧 차감은 이 경우에도 정확히 1회만 일어난다(`ReservationPaymentConcurrencyTest.pay_sameKeyConcurrently_chargesOnceAndReplays`로 검증됨).

### HTTP 상태 코드 기준

| 코드 | 의미 | 예 |
| --- | --- | --- |
| 200 | 조회/처리 성공 | 목록·상세, 취소 성공 |
| 201 | 생성 성공 | 예약(HOLD) 생성, 공간 등록, 도어 토큰 발급 |
| 400 | 요청 형식 오류 | 필수 필드 누락, 형식 위반, 30분 단위 위반, 가격 단위 위반 |
| 401 | 인증 실패 | 토큰 없음/만료/위조 |
| 403 | 인가 실패 | 역할·소유권 불충분, 정지 계정 |
| 404 | 자원 없음 | 존재하지 않는 spaceId/reservationId |
| 409 | 상태 충돌 | 슬롯 중복, 이미 취소/만료된 예약에 대한 요청, 홀드 만료 후 결제 시도, `space.version` 불일치(가격 확인 실패), 공간 운영시간 축소 충돌 |
| 422 | 정책 위반 | 크레딧 잔액 부족, 연장 불가 상태, 관리자 대상 정지/복구 시도, 자기 자신에게 크레딧 지급 |

## 2. 인증 (Auth)

| API | 메서드/경로 | 인증 | 비고 |
| --- | --- | --- | --- |
| 회원가입 | `POST /auth/signup` | 불필요 | `role`은 요청으로 받지 않음, 서버가 항상 USER로 생성. 가입과 동일 트랜잭션에서 `SIGNUP_GRANT` 크레딧 지급(`app.credit.signup-grant`). 오류: VALIDATION_FAILED(400), EMAIL_ALREADY_EXISTS(409) |
| 로그인 | `POST /auth/login` | 불필요 | 성공 시 Access Token은 본문, Refresh Token은 HttpOnly 쿠키로 반환. 오류: INVALID_CREDENTIALS(401), ACCOUNT_INACTIVE(403) |
| 토큰 재발급 | `POST /auth/refresh` | 불필요(refreshToken 쿠키) | 저장된 Refresh Token DB 검증 후 새 Access Token 본문 반환 (Refresh Token 회전은 미구현). 정지 계정은 재발급 차단. 오류: INVALID_REFRESH_TOKEN(401), ACCOUNT_INACTIVE(403) |
| 로그아웃 | `POST /auth/logout` | 불필요(refreshToken 쿠키) | 해당 Refresh Token DB 폐기 후 쿠키 만료(maxAge 0) 응답. 204 No Content |

## 3. 회원 (Member)

- `GET /members/me` (인증 필요): 본인 정보 조회. `balance`(크레딧 잔액) 포함
- `GET /admin/members` (ADMIN): 관리자 회원 목록 조회. 쿼리 파라미터 `status`, `keyword`, `page`, `size` 지원.
- `PATCH /admin/members/{memberId}/suspend` (ADMIN): `reason` 1~500자 필수, 대상은 USER만, `audit_logs` 기록. 오류: FORBIDDEN_ROLE(403), TARGET_IS_ADMIN(422), MEMBER_NOT_FOUND(404)
- `PATCH /admin/members/{memberId}/restore` (ADMIN): suspend와 동일 구조, status → ACTIVE. `audit_logs` 기록.
- `POST /admin/members/{memberId}/credits` (ADMIN): 크레딧 지급(`ADMIN_GRANT`). `amount`(양수), `reason` 1~500자 필수. **자기 자신에게는 지급 불가**(`actor_id != member_id`). **지급만 가능, 회수 없음.** `credit_transaction`과 `audit_logs` 양쪽에 기록. 오류: VALIDATION_FAILED(400), FORBIDDEN_ROLE(403), SELF_GRANT_NOT_ALLOWED(422), MEMBER_NOT_FOUND(404)

## 4. 공간 (Space)

- `GET /spaces` (인증 불필요): `page`, `size`, `keyword`. `status=ACTIVE`인 공간만 기본 노출
- `GET /spaces/{spaceId}` (인증 불필요): 상세 (+ description, `version`). 오류: SPACE_NOT_FOUND(404)
- `GET /spaces/{spaceId}/slots?date=YYYY-MM-DD`: 운영시간을 30분 단위로 쪼갠 예약 가능 여부. **참고용 스냅샷**(실제 확정 여부는 슬롯 INSERT 시점의 UNIQUE 제약으로만 판정 — 사전 조회는 화면 표시용일 뿐 규칙이 아니다)
- `POST /admin/spaces` (ADMIN): `pricePerSlot`은 100원 단위 양수, `openingTime`/`closingTime`은 30분 경계이며 시작이 종료보다 빨라야 한다. 등록자 ID·생성 시각은 서버가 설정하고 `audit_logs`에 기록한다(`REGISTER_SPACE`). 오류: INVALID_OPERATING_HOURS(400), INVALID_PRICE_UNIT(400)
- `PATCH /admin/spaces/{spaceId}` (ADMIN): 부분 수정 + status(ACTIVE/INACTIVE). 배타 잠금(`PESSIMISTIC_WRITE`) 하에 실행. 운영시간 부분 수정도 기존 반대편 시각과 함께 30분 경계·순서를 검증한다. 운영시간 축소 시 현재 이후의 유효 점유 슬롯(`HELD`, `CONFIRMED`, `IN_USE`, `COMPLETED`) 중 새 운영시간 밖 슬롯이 존재하면 `SPACE_OPERATING_HOURS_CONFLICT`(409)로 거절되며 Space와 Audit 모두 변경되지 않는다. 가격 변경 시 `version` 증가(비즈니스 버전) — 이미 확정된 예약의 스냅샷/총액에는 영향 없음. INACTIVE로 바꿔도 기존 예약 유지, 신규 예약만 차단. 수정 완료 시 `audit_logs`에 기록한다(`MODIFY_SPACE`). 오류: SPACE_NOT_FOUND(404), INVALID_OPERATING_HOURS(400), INVALID_PRICE_UNIT(400), SPACE_OPERATING_HOURS_CONFLICT(409)

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

헤더 `Idempotency-Key` 필수(돈이 움직이는 지점). 멱등성 캐시 확인 후, 아래 전 과정을 **하나의 트랜잭션**으로 처리하며 어느 단계에서든 실패하면 전체 롤백한다(예약은 `HOLD`로 남아 만료 전까지 재시도 가능):

1. **소유권 선검증**: 잠금 순서(`Space → Reservation`) 준수를 위해 `reservationId`로부터 `spaceId`와 `memberId`를 먼저 투영 조회하여 본인 예약 여부를 검증(`FORBIDDEN_NOT_OWNER`). 비소유자의 불필요한 락 획득을 차단.
2. **Space 공유 잠금 및 버전 검증**: `spaceId`로 Space 공유 잠금(`findByIdForShare`)을 획득하고, 요청의 `spaceVersion`과 비교. 다르면 즉시 `SPACE_VERSION_MISMATCH`(409)로 거절.
3. **Reservation 조회**: 일반 조회(`findById`)로 예약 정보와 결제 금액 확인.
4. **크레딧 차감**: `creditService.charge()`로 크레딧 차감(잔액 부족 시 `INSUFFICIENT_BALANCE`(422)).
5. **조건부 상태 전이**: `confirmIfHeldAndNotExpired(reservationId, now, HELD, CONFIRMED)` 조건부 UPDATE로 유효한 HOLD에 한해 `CONFIRMED` 전이. 영향 행 0이면 `RESERVATION_STATE_CONFLICT`(409, HOLD 만료 또는 이미 처리됨)를 던져 크레딧 차감을 포함한 트랜잭션 전체를 롤백.
6. 상태 이력 저장 및 멱등성 응답 캐시 저장.

오류: RESERVATION_NOT_FOUND(404), FORBIDDEN_NOT_OWNER(403), SPACE_VERSION_MISMATCH(409), INSUFFICIENT_BALANCE(422), RESERVATION_STATE_CONFLICT(409, 만료/이미결제/취소됨), IDEMPOTENCY_KEY_REQUIRED(400)

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

예약자 본인만. `now < end_time`인 경우만 가능(끝난 예약을 되살리는 것은 연장이 아니라 새 예약). 단일 트랜잭션 처리:
1. **spaceId 투영 조회**: 잠금 순서(`Space → Reservation`) 준수를 위해 `reservationId`로부터 `spaceId`를 먼저 투영 조회.
2. **Space 공유 잠금**: `spaceRepository.findByIdForShare(spaceId)`로 Space 공유 잠금 획득.
3. **Reservation 배타 잠금**: `reservationRepository.findByIdForUpdate(reservationId)`로 비관적 배타 잠금 획득.
4. **검증**: 소유권(`memberId`), 예약 상태(`CONFIRMED` 또는 `IN_USE`), 아직 종료되지 않음(`now < endTime`), `expectedEndTime` 일치 여부 확인. 운영시간 내, 30분 단위, 동일 날짜 등 연장 시간 정책 검증.
5. **슬롯 확보 및 크레딧 차감**: 추가 슬롯 INSERT(`secureSlots`, 충돌 시 409). 원 예약의 `price_per_slot_snapshot` 기준으로 추가 금액 크레딧 차감.
6. **조건부 UPDATE**: `extendIfEndTimeMatches`로 종료 시각과 총액 갱신. 영향 행 0이면 409 롤백.

오류: FORBIDDEN_NOT_OWNER(403), RESERVATION_NOT_FOUND(404), INVALID_RESERVATION_TIME(400, 30분 단위 아님·날짜 넘김·운영 종료 초과), VALIDATION_FAILED(400, 새 종료 시각이 기존보다 늦지 않음), RESERVATION_SLOT_CONFLICT(409, 연장 슬롯 일부/전부 점유), RESERVATION_STATE_CONFLICT(409, `expectedEndTime` 불일치·동시 요청 경합), RESERVATION_EXTEND_NOT_ALLOWED(422, 연장 불가 상태·이미 종료된 예약), INSUFFICIENT_BALANCE(422)

> 실패해도 원 예약은 무손상(기존 슬롯을 건드리지 않음).

### 5-6. 체크아웃 — `POST /reservations/{id}/check-out`

바디 없음. 응답: **200**, `ReservationResponse`(`status: "COMPLETED"`).

예약자 본인만. `WHERE id=:id AND status='IN_USE'` 조건부 UPDATE → `COMPLETED`(`checked_out_at`=now). 같은 트랜잭션에서 활성 토큰 revoke + 상태 이력 저장. 슬롯 반환 없음, 환불 없음. **되돌릴 수 없다**(UI에 확인 다이얼로그).

> 체크인에는 별도 엔드포인트가 없다 — 최초 체크인은 §7 도어 토큰 검증의 부수 효과로 `CONFIRMED → IN_USE` 전이가 일어난다.

오류: FORBIDDEN_NOT_OWNER(403), RESERVATION_NOT_FOUND(404), RESERVATION_STATE_CONFLICT(409)

## 6. [관리자] 예약 관리

- `GET /admin/reservations?page=&size=` (ADMIN): 취소 예약도 목록 포함. 원소 `AdminReservationResponse` = `{ reservationId, memberId, memberEmail, spaceId, spaceName, startTime, endTime, status, totalAmount, createdAt }`. ⚠️ **`date`/`spaceId`/`status` 필터는 현재 미구현**(`AdminReservationSearchCondition`이 빈 클래스) — 프론트는 필터 UI를 비활성화하거나 구현 후 연결
- `GET /admin/reservations/{reservationId}` (ADMIN): 목록 필드 + `pricePerSlotSnapshot`, `cancelledAt`, `checkedInAt`, `checkedOutAt`, `statusHistory[]`, `accessLogs: [{ accessLogId, attemptedAt, result, reasonCode }]`
- `POST /admin/reservations/{reservationId}/force-cancel` (ADMIN): 바디 `{ "reason": "..." }` 1~500자 필수, 응답 `AdminReservationResponse`. 취소 가능 상태는 `HELD`/`CONFIRMED`/`IN_USE`(그 외 종료 상태는 409). 조회 시점 상태 기준 조건부 UPDATE(`WHERE id=:id AND status=:조회 시점 상태`) → 같은 트랜잭션에서 상태 이력(사유 포함) + 슬롯 삭제 + 활성 토큰 revoke + **크레딧 환불(`CONFIRMED`/`IN_USE`는 `total_amount` 전액 `REFUND`, 위약금 없음 / `HELD`는 환불 없음)** + audit_log 기록. 오류: RESERVATION_NOT_FOUND(404), RESERVATION_STATE_CONFLICT(409, 종료 상태이거나 동시 요청에 밀림). **관리자도 이 API 외의 경로로 타인 예약을 취소하거나 도어 토큰을 발급받을 수 없다** (핵심 차별점)

## 7. 출입 (Door Access)

> 현재 구현 경로(접두사 `/api/v1`): `POST /reservations/{id}/door-token`, `PATCH /reservations/{id}/access-token/revoke`, `GET /reservations/{id}/access-logs`, `POST /door-access/verify`. **4개 모두 로그인 필요**(verify도 서버가 로그인 회원이 예약자 본인인지 확인).

- `POST /reservations/{reservationId}/door-token` (예약자 본인만): **발급에는 시간 제한이 없다** — `CONFIRMED` 또는 `IN_USE`이고 `now < end_time`이면 예약 확정 직후부터 언제든 발급 가능(발급은 입장 권한이 아니라 신분증을 받는 것일 뿐, `start` 전엔 문이 열리지 않는다). 기존 활성 토큰은 먼저 폐기 후 재발급. 오류: FORBIDDEN_NOT_OWNER(403, 관리자 포함), RESERVATION_NOT_FOUND(404), RESERVATION_STATE_CONFLICT(409, 같은 예약의 발급 요청이 동시에 겹쳐 활성 토큰 UNIQUE 제약에 밀린 경우 — 재시도하면 새로 발급된다)
- `PATCH /reservations/{reservationId}/access-token/revoke` (예약자 본인만): 해당 예약에 발급된 활성 출입 토큰을 즉시 폐기. 오류: FORBIDDEN_NOT_OWNER(403), RESERVATION_NOT_FOUND(404), ACTIVE_ACCESS_TOKEN_NOT_FOUND(404)
- `GET /reservations/{reservationId}/access-logs` (예약자 본인만): 해당 예약의 출입 기록 목록 조회. 오류: FORBIDDEN_NOT_OWNER(403), RESERVATION_NOT_FOUND(404)
- `POST /door-access/verify`: `{ spaceId, token }`. 검증 순서: ① 해시로 활성 토큰 조회(폐기 토큰은 즉시 거절) ② 예약 상태(`CONFIRMED` 또는 `IN_USE`) 확인, 요청 공간=예약 공간 확인 ③ 시간대 확인 — **최초 체크인: `[start_time, start_time + 15분]`(앞 여유 0분, 시작 시각 정각 허용)**, **재입장: `(checked_in_at, end_time)`(종료 시각 정각은 거절)** ④ 최초 체크인 성공 시 `CONFIRMED → IN_USE` 전이가 부수 효과로 일어남 ⑤ 성공/실패 모두 `door_access_log`에 기록. 거절도 200 + `result: DENY`로 응답. `reasonCode`: TOKEN_NOT_FOUND, TOKEN_REVOKED, RESERVATION_NOT_ACTIVE(취소/완료/노쇼), OUTSIDE_ALLOWED_TIME, SPACE_MISMATCH, MEMBER_MISMATCH

> **2026-09-15 정정** (`core-domain-decisions.md` §8): 기존 "발급 30분 전부터, 최초 체크인 시작 전후 30분" 규칙을 대체한다. 발급 시간 제한을 없애고, 최초 체크인은 앞 여유 0분·뒤 15분(`[start, start+15m]`)으로 변경. 15분 내 미체크인은 배치가 `NO_SHOW`로 전이시키며 슬롯은 반환되지만 환불은 없다.

## 8. 관리자 감사 로그 (Admin Audit Log)

- `GET /api/v1/admin/audit-logs` (ADMIN): 관리자 감사 로그 목록 조회.
  - 쿼리 파라미터(필터):
    - `actorMemberId` (Long): 작업자 ID
    - `action` (AuditAction): `REGISTER_SPACE`, `MODIFY_SPACE`, `SUSPEND_MEMBER`, `REACTIVATE_MEMBER`, `FORCE_CANCEL_RESERVATION`, `GRANT_CREDIT`
    - `targetType` (AuditTargetType): `SPACE`, `MEMBER`, `RESERVATION`
    - `targetId` (Long): 대상 엔티티 ID
    - `dateFrom` (LocalDate, ISO-8601): 조회 시작일 (KST 00:00:00 이상)
    - `dateTo` (LocalDate, ISO-8601): 조회 종료일 (KST 다음 날 00:00:00 미만, 반개구간)
    - `page` (int, default 0), `size` (int, default 20)
  - 정렬: 클라이언트 sort는 무시하고 서버 고정 `createdAt DESC, id DESC` 적용.
  - 응답: `ApiResponse<PageResponse<AuditLogResponse>>` (`id`, `actorMemberId`, `action`, `targetType`, `targetId`, `reason`, `beforeValue`, `afterValue`, `createdAt`)
  - 오류: `VALIDATION_FAILED(400)` (날짜 역전 `dateFrom > dateTo` 또는 파라미터 타입 오류), `AUTHENTICATION_REQUIRED(401)`, `ACCESS_DENIED(403)` (USER)
  - 부수효과: 조회 자체는 감사 로그를 남기지 않음.

## Enum

| 항목 | 값 |
| --- | --- |
| `member.role` | USER, ADMIN |
| `member.status` | ACTIVE, SUSPENDED, WITHDRAWN |
| `space.status` | ACTIVE, INACTIVE |
| `reservation.status` | HELD, EXPIRED, CONFIRMED, IN_USE, COMPLETED, CANCELLED, NO_SHOW |
| `credit_transaction.type` | SIGNUP_GRANT, ADMIN_GRANT, RESERVATION_CHARGE, REFUND, PENALTY |
| `door_access_log.result` | ALLOW, DENY |
| `audit_log.action` | REGISTER_SPACE, MODIFY_SPACE, SUSPEND_MEMBER, REACTIVATE_MEMBER, FORCE_CANCEL_RESERVATION, GRANT_CREDIT |
| `audit_log.target_type` | SPACE, MEMBER, RESERVATION |

> ~~`payment.status`~~ 는 `payment` 테이블 삭제와 함께 제거됨(§1-1, §11). 결제 결과는 `credit_transaction`으로 표현한다.

## 팀 확인 필요 사항 (구현 착수 전 합의 권장)

1. **감사 로그 조회 API**: `GET /api/v1/admin/audit-logs` 구현 완료(2026-09-22). 서버 고정 `createdAt DESC, id DESC` 정렬, KST 반개구간 날짜 필터, Flyway V9 인덱스 `idx_audit_logs_created_id (created_at, id)` 적용.
2. **노쇼 환불률**: 현재 확정값은 0%이며 강사 피드백에 따라 재검토 중(`core-domain-decisions.md` §13). 바뀌면 이 문서와 `credit_transaction.type` 처리 로직도 함께 갱신.

> 2026-09-14 확정: 도어 토큰 발급 시작 시점은 예약 시작 30분 전부터(기획서 4-3 시나리오 "14:30"은 오타 — 14:00 시작 기준 정정값은 13:30으로 규칙과 일치). 최초 체크인 허용 구간은 시작 전후 30분(기존 15분에서 변경)으로 확정.
> 2026-09-15 갱신: 위 2026-09-14 확정 내용은 `core-domain-decisions.md` §8로 다시 대체되었다(발급 시간 제한 없음, 체크인은 `[start, start+15분]`). 이 문서의 "팀 확인 필요 사항"에는 더 이상 해당하지 않으며, §7을 최신 기준으로 본다.
