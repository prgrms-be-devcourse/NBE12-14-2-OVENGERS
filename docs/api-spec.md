# Slot Key API 명세서

> claude.ai 프로젝트 문서 `claude/api-명세서.md` (v0.1, 2026-09-09, 기획서 확정본 기준) 동기화본. 구현 중 세부 규칙이 바뀌면 이 문서도 함께 갱신한다.

## 공통 사항

- Base URL: `/api` (예: `/spaces` → 실제로는 `/api/spaces`)
- 인증: Access Token(JWT, `Authorization: Bearer`), Refresh Token(임의 문자열, 재발급 전용 엔드포인트에서만 사용, 쿠키 전달 시 HttpOnly/Secure/SameSite)
- JWT 클레임은 `sub`(회원 식별자), `exp`만 포함. 역할·상태는 넣지 않고 **모든 보호 API가 요청 시점에 DB에서 재조회**한다.
- 인증 불필요 API: 회원가입, 로그인, 토큰 재발급, 공간 목록/상세 조회, 슬롯 가용성 조회.
- 공통 응답: `{ status, code, message, data }` (성공 SUCCESS/OK, 실패 시 data는 항상 null)
- 페이지네이션: `page`(0-base, 기본 0), `size`(기본 20, 최대 100) → `{ content, page, size, totalElements, totalPages }`
- 시간: ISO-8601, `+09:00` 기준. 예약 시간은 **30분의 배수**만 허용. `Clock` 주입으로 서버 시각 판단.
- `Idempotency-Key`(UUID) 헤더: 결제가 결부된 POST(예약 생성)에 필수. 동일 키 재요청 시 최초 처리 결과를 그대로 반환.

### HTTP 상태 코드 기준

| 코드 | 의미 | 예 |
| --- | --- | --- |
| 200 | 조회/처리 성공 | 목록·상세, 취소 성공 |
| 201 | 생성 성공 | 예약 생성, 공간 등록, 도어 토큰 발급 |
| 400 | 요청 형식 오류 | 필수 필드 누락, 형식 위반 |
| 401 | 인증 실패 | 토큰 없음/만료/위조 |
| 403 | 인가 실패 | 역할·소유권 불충분 |
| 404 | 자원 없음 | 존재하지 않는 spaceId/reservationId |
| 409 | 상태 충돌 | 슬롯 중복, 이미 취소된 예약 재취소 |
| 422 | 정책 위반 | Mock 결제 승인 실패, 운영시간 외, 30분 배수 위반 |

## 2. 인증 (Auth)

| API | 메서드/경로 | 인증 | 비고 |
| --- | --- | --- | --- |
| 회원가입 | `POST /auth/signup` | 불필요 | `role`은 요청으로 받지 않음, 서버가 항상 MEMBER로 생성. 오류: VALIDATION_FAILED(400), EMAIL_ALREADY_EXISTS(409) |
| 로그인 | `POST /auth/login` | 불필요 | 오류: INVALID_CREDENTIALS(401), MEMBER_SUSPENDED(403) |
| 토큰 재발급 | `POST /auth/refresh` | 불필요(refreshToken 바디) | 기존 토큰 즉시 폐기 후 회전. 오류: INVALID_REFRESH_TOKEN(401) |
| 로그아웃 | `POST /auth/logout` | 필요 | 해당 Refresh Token만 폐기, Access Token은 만료까지 유효 |

## 3. 회원 (Member)

- `GET /members/me` (인증 필요): 본인 정보 조회
- `PATCH /admin/members/{memberId}/suspend` (PLATFORM_ADMIN): `reason` 1~500자 필수, 대상은 MEMBER만, audit_log 기록. 오류: FORBIDDEN_ROLE(403), TARGET_IS_ADMIN(422), MEMBER_NOT_FOUND(404)
- `PATCH /admin/members/{memberId}/restore` (PLATFORM_ADMIN): suspend와 동일 구조, status → ACTIVE

## 4. 공간 (Space)

- `GET /spaces` (인증 불필요): `page`, `size`, `keyword`. `status=ACTIVE`인 공간만 기본 노출
- `GET /spaces/{spaceId}` (인증 불필요): 상세 (+ description). 오류: SPACE_NOT_FOUND(404)
- `GET /spaces/{spaceId}/slots?date=YYYY-MM-DD`: 운영시간을 30분 단위로 쪼갠 예약 가능 여부. **참고용 스냅샷**(실제 확정 여부는 예약 생성 시점에 재검증)
- `POST /admin/spaces` (PLATFORM_ADMIN): `pricePerSlot`은 100원 단위 양수만. 등록자 ID·생성 시각은 서버가 설정. audit_log 기록. 오류: VALIDATION_FAILED(400), FORBIDDEN_ROLE(403)
- `PATCH /admin/spaces/{spaceId}` (PLATFORM_ADMIN): 부분 수정 + status(ACTIVE/INACTIVE). INACTIVE로 바꿔도 기존 예약 유지, 신규 예약만 차단. 오류: SPACE_NOT_FOUND(404), FORBIDDEN_ROLE(403), VALIDATION_FAILED(400)

## 5. 예약 및 결제 (Reservation & Payment)

### 5-1. 예약 생성 — `POST /reservations` (MEMBER, 관리자 본인 예약 시 동일 규칙)

헤더 `Idempotency-Key` 필수. 처리 순서:

1. 회원 상태(ACTIVE) 확인
2. 공간 활성 상태, 운영시간 내 여부, 30분 배수 여부, 단일 요금 구간 여부 확인
3. `price_per_slot × 슬롯 수`로 `totalAmount` 계산
4. 슬롯 유니크 제약으로 중복 점유 여부 확인
5. Mock 결제 승인 요청
6. 4~5 모두 성공한 경우에만 `CONFIRMED` 예약 + 가격 스냅샷 + 슬롯 + 최초 상태 이력을 **하나의 트랜잭션**에 저장

오류: VALIDATION_FAILED(400), MEMBER_NOT_ACTIVE(403), SPACE_NOT_FOUND(404), SPACE_INACTIVE(422), RESERVATION_SLOT_CONFLICT(409), PAYMENT_DECLINED(422)

- `GET /reservations?status=&page=&size=` (인증 필요, 본인 예약만)
- `GET /reservations/{reservationId}` (본인 소유만). 오류: RESERVATION_NOT_FOUND(404), FORBIDDEN_NOT_OWNER(403)
- `DELETE /reservations/{reservationId}` (예약자 본인만): 상태가 CONFIRMED인 경우에만 조건부 UPDATE로 CANCELLED. 같은 트랜잭션에서 슬롯 해제 + 도어 토큰 무효화 + 상태 이력 저장. 이후 Mock 환불(결제 완료 건). 환불 실패해도 취소 자체는 200, `refund.status`로 분기. 오류: FORBIDDEN_NOT_OWNER(403), RESERVATION_NOT_FOUND(404), RESERVATION_STATE_CONFLICT(409)

## 6. [관리자] 예약 관리

- `GET /admin/reservations?date=&spaceId=&status=&page=&size=` (PLATFORM_ADMIN): 취소 예약도 목록 포함, 개인정보는 최소 범위만
- `GET /admin/reservations/{reservationId}` (PLATFORM_ADMIN): + `statusHistory` 배열
- `POST /admin/reservations/{reservationId}/force-cancel` (PLATFORM_ADMIN): `reason` 1~500자 필수. 처리 로직은 5-4와 동일 + audit_log 기록. **관리자도 이 API 외의 경로로 타인 예약을 취소하거나 도어 토큰을 발급받을 수 없다** (핵심 차별점)

## 7. 출입 (Door Access)

- `POST /reservations/{reservationId}/door-token` (예약자 본인만): 예약 시작 30분 전부터 발급 가능. 상태가 CONFIRMED이고 종료 전인 경우만 발급. 기존 활성 토큰은 먼저 폐기 후 재발급. 오류: FORBIDDEN_NOT_OWNER(403, 관리자 포함), RESERVATION_NOT_FOUND(404), RESERVATION_NOT_CONFIRMED(422), TOKEN_ISSUE_TOO_EARLY(422)
- `POST /door-access/verify`: `{ spaceId, token }`. 검증 순서: ① 해시로 활성 토큰 조회(폐기 토큰은 즉시 거절) ② 예약 상태(CONFIRMED)/요청 공간=예약 공간 확인 ③ 시간대 확인 — 최초 체크인: 시작 30분 전~30분 후, 재입장: 최초 체크인 후~종료 전, 종료 후는 체크인 여부 무관 거절 ④ 성공/실패 모두 `door_access_log`에 기록. 거절도 200 + `result: DENY`로 응답. `reasonCode`: TOKEN_NOT_FOUND, TOKEN_REVOKED, RESERVATION_CANCELLED, RESERVATION_COMPLETED, OUTSIDE_ALLOWED_TIME, SPACE_MISMATCH

## Enum

| 항목 | 값 |
| --- | --- |
| `member.role` | MEMBER, PLATFORM_ADMIN |
| `member.status` | ACTIVE, SUSPENDED |
| `space.status` | ACTIVE, INACTIVE |
| `reservation.status` | CONFIRMED, CANCELLED, COMPLETED |
| `payment.status` | SUCCESS, CANCELLED |
| `door_access_log.result` | ALLOW, DENY |

## 팀 확인 필요 사항 (구현 착수 전 합의 권장)

1. **감사 로그 조회 API**: `GET /admin/audit-logs`는 DoD에 명시되어 있지 않음. 필요 시 추가 정의(우선순위 낮음, MVP 3개 기능 범위 밖).

> 2026-09-14 확정: 도어 토큰 발급 시작 시점은 예약 시작 30분 전부터(기획서 4-3 시나리오 "14:30"은 오타 — 14:00 시작 기준 정정값은 13:30으로 규칙과 일치). 최초 체크인 허용 구간은 시작 전후 30분(기존 15분에서 변경)으로 확정.
