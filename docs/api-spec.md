# Slot Key API 명세서

> claude.ai 프로젝트 문서 `claude/api-명세서.md` (v0.1, 2026-09-09, 기획서 확정본 기준) 동기화본. 구현 중 세부 규칙이 바뀌면 이 문서도 함께 갱신한다.
> **2026-09-15 갱신**: `docs/core-domain-decisions.md`(확정본) 반영 — 예약 생성이 1단계(즉시 결제)에서 2단계(HOLD → 결제 확인)로 변경, `payment` 삭제·`credit_transaction` 도입, 연장/체크아웃/크레딧 지급 엔드포인트 신규, 도어 토큰 발급·체크인 시간 규칙 변경. 충돌 시 `core-domain-decisions.md`가 우선한다.
> **2026-09-17 갱신 (docs/api)**: 실제 컨트롤러 코드 기준으로 동기화 — Base URL `/api/v1`, 취소 API `POST .../cancel`, 요청/응답 필드, 관리자 예약 목록 필터 미지원, 미구현 API 표시. **프론트는 이 문서를 기준으로 한다.** (claude.ai 프로젝트 문서 `claude/api-명세서.md`는 구버전 — 참고 금지)
> **2026-09-24 갱신 (대단위 1)**: 컨트롤러·DTO·`SecurityConfig`·`ErrorCode` 전수 조사 기반 정합성 동기화 — 실제 응답 DTO 필드, 엔드포인트별 페이지네이션 반환 형태(`PageResponse` vs Spring `Page`), 오프셋 없는 ISO-8601 LocalDateTime(KST), 멱등성 캐시 동작, 실존 `ErrorCode` 반영 및 미구현/결정 필요 항목(`termsVersion`, 동일 멱등키 충돌 검사, 정지 계정 즉시 차단) 분리 명시.

## 공통 사항

- Base URL: `/api/v1` (예: `/spaces` → 실제로는 `/api/v1/spaces`)
  - 출입(§7) API도 `/api/v1`로 통일됨(2026-09-17, 기존 `/api`).
- 인증: Access Token(JWT, `Authorization: Bearer <token>`), Refresh Token(DB 저장, `POST /auth/refresh`, `POST /auth/logout`에서 HttpOnly 쿠키로 전달, Path=`/api/v1/auth`, maxAge=1일).
- JWT 페이로드는 `id`, `email`, `role`, 만료 시각(`exp`)을 포함한다. 보호 API 요청 시 서명과 만료 시각을 검증하여 인증하며, **매 요청마다 회원 DB를 재조회하지 않는다**. 비즈니스 서비스(`ReservationHoldService`, `DoorAccessVerificationService` 등)에서도 회원 `ACTIVE` 여부를 별도 재조회하지 않으므로, 회원이 정지되어도 기존 Access Token은 만료 전까지 기술적으로 요청이 통과될 수 있다. 토큰 재발급(`POST /auth/refresh`) 시에만 DB 조회를 거쳐 `ACCOUNT_INACTIVE`(403)로 차단된다. (정지 즉시 기존 토큰 및 서비스 차단은 **결정 필요** 항목으로 관리)
- 인증 불필요 API: 회원가입, 로그인, 토큰 재발급, 로그아웃, 공간 목록/상세 조회, 슬롯 가용성 조회, 공간 이미지 공개 조회(`GET /space-images/{fileName}`).
- 공통 응답: `{ status, code, message, data }` (`status`: SUCCESS | FAIL, 성공 시 `code`: "OK"). 단, `ApiResponse` 클래스에 `@JsonInclude(JsonInclude.Include.NON_NULL)`가 적용되어 있어 Java 상에서 `data == null`인 경우(실패 응답 또는 `ApiResponse<Void>`) 실제 HTTP JSON 직렬화 시 `"data"` 필드 자체가 생략된다.
- 페이지네이션 형식 (엔드포인트별 구분):
  - 공통 래퍼 `PageResponse<T>` (`content`, `page`, `size`, `totalElements`, `totalPages`): 공간 목록(`GET /spaces`), 관리자 공간 목록(`GET /admin/spaces`), 관리자 회원 목록(`GET /admin/members`), 관리자 감사 로그(`GET /admin/audit-logs`), 문의 목록(`GET /inquiries`), 관리자 문의 목록(`GET /admin/inquiries`)
  - Spring Data `Page<T>` 직렬화 (`content`, `pageable`, `totalElements`, `totalPages`, `last`, `size`, `number`, `sort`, `first`, `numberOfElements`, `empty` 등): 내 예약 목록(`GET /reservations`), 관리자 예약 목록(`GET /admin/reservations`)
- 시간: ISO-8601 문자열. 서버 직렬화는 타임존 오프셋 표기가 없는 `LocalDateTime` 형식(`"yyyy-MM-dd'T'HH:mm:ss"`)이며, 서버 내부 기준시계는 KST(`Asia/Seoul`, `Clock`)이다. 클라이언트는 이 문자열을 한국 표준시(KST)로 해석하는 것이 설계 목표이다. *(주의: 현재 프론트 `HoldCountdown.tsx`는 `new Date(holdExpiresAt)`로 브라우저 로컬 시간대로 직접 파싱하고 있어 비-KST 환경에서 오차 가능성이 있으며, 시간 파싱 코드 정정은 별도 프론트 코드 과제로 추적한다)*. 날짜는 `"yyyy-MM-dd"`, 시각은 `"HH:mm:ss"`. 예약 시간은 **30분의 배수**만 허용.
- `Idempotency-Key`(UUID) 헤더:
  - **실제로 돈이 움직이는 `POST /reservations/{id}/pay`에만 필수** (누락 시 400 `IDEMPOTENCY_KEY_REQUIRED`). `POST /reservations`(HOLD 생성)는 결제가 없으므로 대상 아님.
  - **캐시 동작**: **성공 응답(200 OK)만 캐시 테이블에 저장**되며, 결제 실패 시에는 트랜잭션이 롤백되어 캐시되지 않는다. 동일 키로 재요청 시 성공했던 건은 저장된 응답을 그대로 재생 반환한다.
  - **동시 경합**: 캐시 조회와 저장이 분리되어 있어 극히 좁은 시간차로 동시 진입 시 한쪽은 정상 처리되고 다른 쪽은 `409 RESERVATION_STATE_CONFLICT`를 받을 수 있다. 409를 받은 쪽이 동일 키로 재시도하면 저장된 최초 성공 응답을 돌려받는다. 크레딧 차감은 1회만 발생한다(`ReservationPaymentConcurrencyTest` 검증).
  - *(결정 필요)*: 동일 키에 다른 요청 본문이 들어왔을 때의 충돌 감지(`IDEMPOTENCY_KEY_CONFLICT`) 및 실패 응답 캐싱, TTL 만료 보관 정책은 현재 미구현 상태이다.
- OpenAPI / Swagger 검증 상태 구분:
  - 공간 이미지 관련 OpenAPI 3.0 명세(PUT/GET 파라미터·바이너리·헤더) 및 Swagger UI HTML 접근성은 `SpaceOpenApiDocumentationTest`(2개 테스트)로 자동 검증됨 (`/swagger-ui/index.html` GET 200 검증 포함. 과거 2026-09-23 실행 기록: 공간 이미지 관련 5개 테스트 클래스 XML 합계 34개 통과). API 전체 DTO 스키마 전수 검증이나 이번 문서 최신화 직후의 테스트 재실행 근거는 아님.
  - Swagger UI 진입/리다이렉트 경로(`/swagger-ui.html`) 및 HTML 리소스(`/swagger-ui/index.html`)가 구성되어 있으나, 자동화 테스트로 GET 200이 직접 확인된 대상 경로는 `/swagger-ui/index.html` 1건이다.
  - 브라우저를 통한 `Try it out` 대화형 실사용 테스트는 미검증 상태임.

### HTTP 상태 코드 기준

| 코드 | 의미 | 예 |
| --- | --- | --- |
| 200 | 조회/처리 성공 | 목록·상세, 결제 확정, 취소 성공 |
| 201 | 생성 성공 | 예약(HOLD) 생성, 회원가입, 공간 등록, 도어 토큰 발급, 문의 등록 |
| 204 | 본문 없음 성공 | 로그아웃 |
| 400 | 요청 형식 오류 | 필수 필드 누락, 형식 위반, 30분 단위 위반, 가격 단위 위반, 멱등키 누락, 이미지 형식/해상도 위반, 문의 입력값 오류 |
| 401 | 인증 실패 | 토큰 없음(`AUTHENTICATION_REQUIRED`), 토큰 만료/위조(`INVALID_ACCESS_TOKEN`, `INVALID_REFRESH_TOKEN`), 로그인 자격증명 불일치(`INVALID_CREDENTIALS`) |
| 403 | 인가 실패 | 역할 불충분(`ACCESS_DENIED`, `FORBIDDEN_ROLE`), 소유권 불일치(`FORBIDDEN_NOT_OWNER`), 정지 계정 로그인/재발급 거절(`ACCOUNT_INACTIVE`) |
| 404 | 자원 없음 | 존재하지 않는 spaceId(`SPACE_NOT_FOUND`), reservationId(`RESERVATION_NOT_FOUND`), memberId(`MEMBER_NOT_FOUND`), inquiryId(`INQUIRY_NOT_FOUND`), fileName(`IMAGE_NOT_FOUND`) |
| 409 | 상태 충돌 | 슬롯 중복(`RESERVATION_SLOT_CONFLICT`), 이미 취소/만료/확정된 예약에 대한 요청(`RESERVATION_STATE_CONFLICT`), `space.version` 불일치(`SPACE_VERSION_MISMATCH`), 공간 운영시간 축소 충돌(`SPACE_OPERATING_HOURS_CONFLICT`), 이메일 중복(`EMAIL_ALREADY_EXISTS`) |
| 413 | 페이로드 초과 | 공간 대표 사진 파일 크기 5MB 초과(`IMAGE_SIZE_EXCEEDED`) |
| 422 | 비즈니스 규칙 위반 | 크레딧 잔액 부족(`INSUFFICIENT_BALANCE`), 연장 불가(`RESERVATION_EXTEND_NOT_ALLOWED`), 비활성 공간(`SPACE_INACTIVE`), 관리자 정지/복구 불가(`TARGET_IS_ADMIN`), 자기 자신 크레딧 지급 불가(`SELF_GRANT_NOT_ALLOWED`), 답변 완료 문의 수정 불가(`INQUIRY_ALREADY_ANSWERED`) |
| 500 | 서버 오류 | 이미지 디스크 저장 실패(`IMAGE_STORAGE_ERROR`) |

## 2. 인증 (Auth)

| API | 메서드/경로 | 인증 | 비고 |
| --- | --- | --- | --- |
| 회원가입 | `POST /auth/signup` | 불필요 | `role`은 요청으로 받지 않음, 서버가 항상 USER로 생성. 가입 트랜잭션에서 `SIGNUP_GRANT` 크레딧 지급(`app.credit.signup-grant`). 오류: `VALIDATION_FAILED`(400), `PASSWORD_CONFIRM_MISMATCH`(400), `EMAIL_ALREADY_EXISTS`(409) |
| 로그인 | `POST /auth/login` | 불필요 | 성공 시 Access Token은 본문, Refresh Token은 HttpOnly 쿠키(Path=`/api/v1/auth`, maxAge=1일)로 반환. 오류: `VALIDATION_FAILED`(400), `INVALID_CREDENTIALS`(401), `ACCOUNT_INACTIVE`(403) |
| 토큰 재발급 | `POST /auth/refresh` | 불필요(refreshToken 쿠키) | 저장된 Refresh Token DB 검증 후 새 Access Token 본문 반환 (Refresh Token 회전은 미구현). 정지 계정은 재발급 차단. 오류: `INVALID_REFRESH_TOKEN`(401), `ACCOUNT_INACTIVE`(403) |
| 로그아웃 | `POST /auth/logout` | 불필요(refreshToken 쿠키) | 해당 Refresh Token DB 폐기 후 쿠키 만료(maxAge 0) 응답. 204 No Content |

## 3. 회원 (Member)

- `GET /members/me` (인증 필요): 본인 정보 조회. 응답 `MemberResponse` (`id`, `email`, `nickname`, `role`, `status`, `balance`, `createdAt`). 오류: `AUTHENTICATION_REQUIRED`(401), `INVALID_ACCESS_TOKEN`(401)
- `GET /admin/members` (ADMIN): 관리자 회원 목록 조회. 쿼리 파라미터 `status`, `keyword`, `page`, `size` 지원. 응답 `ApiResponse<PageResponse<AdminMemberResponse>>` (원소: `id`, `email`, `nickname`, `role`, `status`, `balance`, `createdAt`, `lastStatusChangedAt`, `lastStatusChangeReason`). 오류: `AUTHENTICATION_REQUIRED`(401), `ACCESS_DENIED`(403), `FORBIDDEN_ROLE`(403)
- `PATCH /admin/members/{memberId}/suspend` (ADMIN): `reason` 1~500자 필수, 대상은 USER만, `audit_logs` 기록. 응답 `MemberStatusChangeResponse`. 오류: `VALIDATION_FAILED`(400), `AUTHENTICATION_REQUIRED`(401), `ACCESS_DENIED`(403), `MEMBER_NOT_FOUND`(404), `TARGET_IS_ADMIN`(422)
- `PATCH /admin/members/{memberId}/restore` (ADMIN): suspend와 동일 구조, status → ACTIVE. `audit_logs` 기록. 응답 `MemberStatusChangeResponse`. 오류: `VALIDATION_FAILED`(400), `AUTHENTICATION_REQUIRED`(401), `ACCESS_DENIED`(403), `MEMBER_NOT_FOUND`(404), `TARGET_IS_ADMIN`(422)
- `POST /admin/members/{memberId}/credits` (ADMIN): 크레딧 지급(`ADMIN_GRANT`). `amount`(양수), `reason` 1~500자 필수. **자기 자신에게는 지급 불가**(`actor_id != member_id`). **지급만 가능, 회수 없음.** `credit_transaction`과 `audit_logs` 양쪽에 기록. 응답 `AdminMemberResponse`. 오류: `VALIDATION_FAILED`(400), `AUTHENTICATION_REQUIRED`(401), `ACCESS_DENIED`(403), `MEMBER_NOT_FOUND`(404), `SELF_GRANT_NOT_ALLOWED`(422)

## 4. 공간 (Space)

- `GET /spaces` (인증 불필요): `page`, `size`, `keyword`. `status=ACTIVE`인 공간만 기본 노출. 응답 `ApiResponse<PageResponse<SpaceResponse>>` (원소: `id`, `name`, `location`, `capacity`, `pricePerSlot`, `imagePath`, `openingTime`, `closingTime`, `status`)
  - **2026-09-23 추가(오피스 찾기 필터)**: `location`(문자열, 부분일치), `minPrice`/`maxPrice`(Long, `pricePerSlot` 범위)
    - **주의**: `location`은 지역 전용 컬럼이 아니라 공간의 상세 위치 문자열이다(`data-setting/sql/setup.sql` 샘플 기준 값은 정확히 `판교` / `하남` / `강남` 셋 중 하나 — **`한남`이 아니라 `하남`**이니 프론트 드롭다운 값은 이 세 값을 그대로 써야 한다). 그 외 수동 등록된 공간은 `서울시 강남구 테헤란로`처럼 상세 주소가 들어있을 수 있어 부분일치로 매치된다.
  - **2026-09-23 추가(시간대 가용성 필터)**: `date`+`startTime`+`endTime`(HH:mm:ss)을 셋 다 지정하면 그 시간대에 점유된 슬롯이 하나도 없는 공간만 반환(참고용 스냅샷 — 실제 예약 가능 여부는 예약 생성 시점에 재검증). 셋 중 하나라도 빠지면 시간 필터는 적용되지 않는다. `startTime >= endTime`이면 `INVALID_TIME_RANGE`(400)
- `GET /spaces/{spaceId}` (인증 불필요): 상세. 응답 `SpaceDetailResponse` (`id`, `name`, `location`, `description`, `capacity`, `pricePerSlot`, `imagePath`, `openingTime`, `closingTime`, `status`, `version`, `createdAt`, `updatedAt`). 오류: `SPACE_NOT_FOUND`(404)
- `GET /spaces/{spaceId}/slots?date=YYYY-MM-DD` (인증 불필요): 운영시간을 30분 단위로 쪼갠 예약 가능 여부. 응답 `SpaceSlotAvailabilityResponse` (`spaceId`, `date`, `slots: [{ startTime, endTime, isAvailable }]`). **참고용 스냅샷**(실제 확정 여부는 슬롯 INSERT 시점의 UNIQUE 제약으로만 판정 — 사전 조회는 화면 표시용일 뿐 규칙이 아니다). 오류: `VALIDATION_FAILED`(400), `SPACE_NOT_FOUND`(404)
- `GET /admin/spaces` (ADMIN): 관리자 공간 목록 조회. 쿼리 파라미터 `status`, `keyword`, `page`, `size`. 응답 `ApiResponse<PageResponse<SpaceDetailResponse>>`. 오류: `AUTHENTICATION_REQUIRED`(401), `ACCESS_DENIED`(403)
- `GET /admin/spaces/{spaceId}` (ADMIN): 관리자 공간 상세 조회. 응답 `SpaceDetailResponse`. 오류: `AUTHENTICATION_REQUIRED`(401), `ACCESS_DENIED`(403), `SPACE_NOT_FOUND`(404)
- `POST /admin/spaces` (ADMIN): `pricePerSlot`은 100원 단위 양수, `openingTime`/`closingTime`은 30분 경계이며 시작이 종료보다 빨라야 한다. 등록자 ID·생성 시각은 서버가 설정하고 `audit_logs`에 기록한다(`REGISTER_SPACE`). 본문에 비어 있지 않은 `imagePath`를 전달하면 `VALIDATION_FAILED`(400)로 거절되며, 사진은 등록 후 사진 업로드 API를 통해 설정한다. 응답: 201, `data` = `SpaceDetailResponse`. 오류: `VALIDATION_FAILED`(400), `INVALID_OPERATING_HOURS`(400), `INVALID_PRICE_UNIT`(400), `AUTHENTICATION_REQUIRED`(401), `ACCESS_DENIED`(403)
- `PATCH /admin/spaces/{spaceId}` (ADMIN): 부분 수정 + status(ACTIVE/INACTIVE). 배타 잠금(`PESSIMISTIC_WRITE`) 하에 실행. 운영시간 부분 수정도 기존 반대편 시각과 함께 30분 경계·순서를 검증한다. 운영시간 축소 시 현재 이후의 유효 점유 슬롯(`HELD`, `CONFIRMED`, `IN_USE`, `COMPLETED`) 중 새 운영시간 밖 슬롯이 존재하면 `SPACE_OPERATING_HOURS_CONFLICT`(409)로 거절되며 Space와 Audit 모두 변경되지 않는다. 가격 변경 시 `version` 증가(비즈니스 버전) — 이미 확정된 예약의 스냅샷/총액에는 영향 없음. 본문에 비어 있지 않은 `imagePath`를 전달하면 `VALIDATION_FAILED`(400)로 거절되며, 사진 수정은 별도 업로드 API를 사용한다. INACTIVE로 바꿔도 기존 예약 유지, 신규 예약만 차단. 수정 완료 시 `audit_logs`에 기록한다(`MODIFY_SPACE`). 응답: 200, `data` = `SpaceDetailResponse`. 오류: `SPACE_NOT_FOUND`(404), `VALIDATION_FAILED`(400), `INVALID_OPERATING_HOURS`(400), `INVALID_PRICE_UNIT`(400), `SPACE_OPERATING_HOURS_CONFLICT`(409), `AUTHENTICATION_REQUIRED`(401), `ACCESS_DENIED`(403)
- `PUT /admin/spaces/{spaceId}/image` (ADMIN, `multipart/form-data`): 대표 사진 파일(`file`, 5MB 이하, JPEG/PNG, 가로·세로 최대 4096px) 업로드. 바이너리 서명 및 해상도 사전 검증 후 UUID 파일명으로 디스크에 안전하게 저장하고 `Space.imagePath`(`/api/v1/space-images/{fileName}`)를 갱신하며 `audit_logs`에 기록한다(`MODIFY_SPACE`). 가격 변경이 아니므로 `version`은 증가하지 않는다. 이전 이미지가 서버 관리 이미지인 경우 파일 시스템에서 정리한다(샘플 `/images/...`는 삭제하지 않음). 응답: 200, `data` = `SpaceDetailResponse`. 오류: `IMAGE_FILE_EMPTY`(400), `INVALID_IMAGE_FORMAT`(400), `IMAGE_DIMENSIONS_EXCEEDED`(400), `AUTHENTICATION_REQUIRED`(401), `ACCESS_DENIED`(403), `SPACE_NOT_FOUND`(404), `IMAGE_SIZE_EXCEEDED`(413), `IMAGE_STORAGE_ERROR`(500)
- `GET /space-images/{fileName}` (인증 불필요): 업로드된 공간 대표 사진 정적 스트리밍. 경로 탈출 방어, 올바른 MIME 반환(`image/jpeg`, `image/png`), `Cache-Control: public, max-age=86400`, `X-Content-Type-Options: nosniff` 헤더 제공. 응답: 바이너리 리소스. 오류: `IMAGE_NOT_FOUND`(404, JSON ApiResponse)

관리자 화면의 등록·교체·부분 실패 복구와 Spring MVC 파일 저장 흐름은 [관리자 공간 대표 사진 안내](admin-space-image-upload.md)의 다이어그램을 참고한다. 공간 이미지 OpenAPI 스키마 생성 및 Swagger UI HTML 리소스 서빙(`/swagger-ui/index.html`)은 `SpaceOpenApiDocumentationTest`(2개 테스트)로 검증되었으며(`/swagger-ui.html`은 진입 리다이렉트 경로), 파일 업로드/조회 흐름은 과거(2026-09-23) 실행된 이미지 관련 5개 테스트 클래스(XML 합계 34개)로 통과 기록이 확인되었다. 단, 브라우저를 통한 `Try it out` 수동 실사용은 아직 미검증 상태이다. 사진 PUT 실패 시 공간 저장은 유지되므로 동일 spaceId로 PUT만 재시도한다.

## 5. 예약 · 크레딧 (Reservation & Credit)

> **프론트 예약 흐름 요약**: ① `POST /reservations`로 HOLD 생성(201) → 응답의 `reservationId`, `spaceVersion`, `holdExpiresAt` 보관 → ② 10분 안에 `POST /reservations/{id}/pay`(헤더 `Idempotency-Key`, 바디 `spaceVersion`)로 확정. 한 번의 호출로 결제까지 끝나지 않는다. 응답에 `payment` 객체는 없다.

#### 1) 공통 응답 DTO `ReservationResponse` (HOLD 생성 / 결제 확인 / 연장 / 체크아웃 / 취소 단건 응답)

```json
{
  "reservationId": 1,
  "spaceId": 3,
  "startTime": "2026-09-20T14:00:00",
  "endTime": "2026-09-20T15:00:00",
  "status": "HELD",
  "pricePerSlotSnapshot": 5000,
  "totalAmount": 10000,
  "holdExpiresAt": "2026-09-17T13:10:00",
  "checkedInAt": null,
  "checkedOutAt": null,
  "cancelledAt": null,
  "spaceVersion": 2,
  "refundAmount": null,
  "penaltyAmount": null,
  "createdAt": "2026-09-17T13:00:00"
}
```

- `spaceVersion`: **HOLD 생성 응답에서만** 정수값 반환(그 외 null). 클라이언트가 결제 요청(`POST .../pay`) 시 그대로 전달한다.
- `refundAmount`/`penaltyAmount`: **취소 응답에서만** 정수값 반환(그 외 null).
- 시간 필드는 오프셋 없는 `LocalDateTime` 문자열(서버 기준 KST).

#### 2) 내 예약 목록 DTO `ReservationListResponse` (`GET /reservations` 목록 원소)

```json
{
  "reservationId": 1,
  "spaceId": 3,
  "spaceName": "집중 회의실 A",
  "spaceLocation": "판교",
  "spaceImagePath": "/api/v1/space-images/uuid.jpg",
  "date": "2026-09-20",
  "startTime": "2026-09-20T14:00:00",
  "endTime": "2026-09-20T15:00:00",
  "status": "CONFIRMED",
  "pricePerSlotSnapshot": 5000,
  "totalAmount": 10000,
  "holdExpiresAt": null,
  "checkedInAt": null,
  "checkedOutAt": null,
  "cancelledAt": null,
  "createdAt": "2026-09-17T13:00:00"
}
```

- 목록 원소에는 공간 기본 정보(`spaceName`, `spaceLocation`, `spaceImagePath`) 및 `date`가 포함된다.
- `spaceVersion`, `refundAmount`, `penaltyAmount`는 포함되지 않는다.

#### 3) 내 예약 상세 DTO `ReservationDetailResponse` (`GET /reservations/{reservationId}` 응답)

```json
{
  "reservationId": 1,
  "spaceId": 3,
  "startTime": "2026-09-20T14:00:00",
  "endTime": "2026-09-20T15:00:00",
  "status": "CONFIRMED",
  "pricePerSlotSnapshot": 5000,
  "totalAmount": 10000,
  "holdExpiresAt": null,
  "checkedInAt": null,
  "checkedOutAt": null,
  "cancelledAt": null,
  "createdAt": "2026-09-17T13:00:00",
  "statusHistory": [
    {
      "fromStatus": null,
      "toStatus": "HELD",
      "reason": null,
      "changedAt": "2026-09-17T13:00:00"
    },
    {
      "fromStatus": "HELD",
      "toStatus": "CONFIRMED",
      "reason": null,
      "changedAt": "2026-09-17T13:02:00"
    }
  ]
}
```

- `ReservationResponse`의 기본 필드와 `statusHistory` 목록으로 구성된다.
- **주의**: `spaceVersion`, `refundAmount`, `penaltyAmount`는 포함되지 않는다. 또한 공간 정보(`spaceName`, `spaceLocation`, `spaceImagePath`)도 포함되지 않으므로, 프론트엔드는 공간 상세 조회(`GET /spaces/{spaceId}`)를 함께 호출하여 화면 모델을 보강한다.

### 5-1. 예약 생성(HOLD) — `POST /reservations`

요청 바디 (`ReservationCreateRequest`):

```json
{
  "spaceId": 3,
  "date": "2026-09-20",
  "startTime": "14:00:00",
  "endTime": "15:00:00"
}
```

> **프론트 연동 주의 (`termsVersion`)**: 프론트엔드(`SpaceDetailPage.tsx`, `enums.ts`의 `TERMS_VERSION`)는 현재 `{ spaceId, date, startTime, endTime, termsVersion: 'v1.1' }` 5개 필드를 전송하고 있으나, 백엔드 `ReservationCreateRequest` 및 엔티티/DDL에는 `termsVersion`이 정의되어 있지 않아 서버에서 수신·저장되지 않는다(Jackson 기본 설정에 의해 오류 없이 무시됨). 약관 버전의 서버 수신 및 DB 저장은 **결정 필요** 항목으로 관리한다.

응답: **201**, `ApiResponse<ReservationResponse>` (`status: "HELD"`, `spaceVersion: <number>` 포함).

`Idempotency-Key` 불필요(이 단계는 결제가 없음). 처리 순서:

1. 시간 및 요청 형식 검증: 30분 단위, 운영시간 내, 당일 내 종료 여부 검증 (위반 시 `INVALID_RESERVATION_TIME`(400) 또는 `VALIDATION_FAILED`(400))
2. 공간 상태 검증: 공간 존재 여부(`SPACE_NOT_FOUND`(404)) 및 ACTIVE 여부 확인 (`SPACE_INACTIVE`(422)). *(참고: `ReservationHoldService`에서는 회원 ACTIVE 상태를 별도 재검사하지 않음)*
3. 대상 슬롯의 만료된 HELD를 조건부 UPDATE로 EXPIRED 전이 + 슬롯 삭제(즉시 정리)
4. `price_per_slot × 슬롯 수`로 `totalAmount` 계산
5. 슬롯 유니크 INSERT 시도 → 충돌 시 `RESERVATION_SLOT_CONFLICT`(409)
6. `Reservation` 생성(`HELD`, `holdExpiresAt` = now + 10분) 및 상태 이력 저장. 응답에 `totalAmount`, `spaceVersion`, `holdExpiresAt` 반환

오류: `VALIDATION_FAILED`(400), `INVALID_RESERVATION_TIME`(400), `AUTHENTICATION_REQUIRED`(401), `SPACE_NOT_FOUND`(404), `RESERVATION_SLOT_CONFLICT`(409), `SPACE_INACTIVE`(422)

### 5-2. 결제 확인 및 확정 — `POST /reservations/{id}/pay`

요청:
- 헤더: `Authorization: Bearer <token>`, `Idempotency-Key: <UUID>` (누락 시 400 `IDEMPOTENCY_KEY_REQUIRED`)
- 바디 (`ReservationPayRequest`): `{ "spaceVersion": 2 }`

응답: **200**, `ApiResponse<ReservationResponse>` (`status: "CONFIRMED"`).

헤더 `Idempotency-Key` 필수(돈이 움직이는 지점). 멱등성 캐시 확인 후, 아래 전 과정을 **하나의 트랜잭션**으로 처리하며 어느 단계에서든 실패하면 전체 롤백한다(예약은 `HOLD`로 남아 만료 전까지 재시도 가능):

1. **Idempotency-Key 캐시 확인**: 이전에 동일 키와 사용자 ID, 경로로 처리된 성공 응답(200 OK)이 있으면 즉시 반환.
2. **소유권 선검증**: 잠금 순서(`Space → Reservation`) 준수를 위해 `reservationId`로부터 `spaceId`와 `memberId`를 먼저 투영 조회하여 본인 예약 여부를 검증 (`FORBIDDEN_NOT_OWNER`(403)). 비소유자의 불필요한 락 획득을 차단.
3. **Space 공유 잠금 및 버전 검증**: `spaceId`로 Space 공유 잠금(`findByIdForShare`)을 획득하고, 요청의 `spaceVersion`과 비교. 다르면 즉시 `SPACE_VERSION_MISMATCH`(409)로 거절.
4. **Reservation 조회**: 일반 조회(`findById`)로 예약 정보와 결제 금액 확인 (`RESERVATION_NOT_FOUND`(404)).
5. **크레딧 차감**: `creditService.charge()`로 크레딧 차감(잔액 부족 시 `INSUFFICIENT_BALANCE`(422)).
6. **조건부 상태 전이**: `confirmIfHeldAndNotExpired(reservationId, now, HELD, CONFIRMED)` 조건부 UPDATE로 유효한 HOLD에 한해 `CONFIRMED` 전이. 영향 행 0이면 `RESERVATION_STATE_CONFLICT`(409, HOLD 만료 또는 이미 확정/취소됨)를 던져 크레딧 차감을 포함한 트랜잭션 전체를 롤백.
7. 상태 이력 저장 및 멱등성 성공 응답(200) 캐시 저장.

오류: `IDEMPOTENCY_KEY_REQUIRED`(400), `VALIDATION_FAILED`(400), `AUTHENTICATION_REQUIRED`(401), `FORBIDDEN_NOT_OWNER`(403), `RESERVATION_NOT_FOUND`(404), `SPACE_NOT_FOUND`(404), `SPACE_VERSION_MISMATCH`(409), `RESERVATION_STATE_CONFLICT`(409), `INSUFFICIENT_BALANCE`(422)

> 💡 **오류 코드 참고**: HOLD 만료로 결제가 거절될 때 반환되는 에러 코드는 `RESERVATION_HOLD_EXPIRED`가 아니라 `RESERVATION_STATE_CONFLICT`(409)이다. 프론트엔드는 이 코드를 받아 만료 안내를 표시한다.

### 5-3. 조회

- `GET /reservations?status=&page=&size=` (인증 필요, 본인 예약만). 응답 `ApiResponse<Page<ReservationListResponse>>`. 반환 `data`는 Spring Data `Page` 직렬화 형태(`content`, `pageable`, `totalElements`, `totalPages`, `last`, `size`, `number`, `sort`, `first`, `numberOfElements`, `empty` 등). `status` 생략 시 전체. 원소는 `ReservationListResponse` (공간명, 사진, 위치 포함).
- `GET /reservations/{reservationId}` (인증 필요, 본인 예약만). 응답 `ApiResponse<ReservationDetailResponse>`. 상태 이력(`statusHistory`)이 포함되며, 공간명 등 상세는 프론트가 공간 API를 통해 병합 표시. 오류: `AUTHENTICATION_REQUIRED`(401), `FORBIDDEN_NOT_OWNER`(403), `RESERVATION_NOT_FOUND`(404)

### 5-4. 취소 — `POST /reservations/{reservationId}/cancel`

> ⚠️ `DELETE /reservations/{id}`가 아니다(2026-09-17 정정). 바디 없음. 응답: **200**, `ApiResponse<ReservationResponse>` (`status: "CANCELLED"`, `refundAmount`, `penaltyAmount` 포함).

예약자 본인만. 시작 1시간 전까지 100% 환불, 1시간 전~시작 전 50% 환불, 시작 이후는 취소 불가(체크아웃으로만 종료).

1. 조건부 UPDATE: `WHERE id=:id AND status='CONFIRMED' AND :now < start_time` → `CANCELLED`(`cancelled_at`=now). 영향 행 0이면 409
2. (같은 트랜잭션) 슬롯 삭제 + 활성 토큰 revoke + 크레딧 환급(`REFUND` 전액, 필요시 `PENALTY` 위약금을 **두 줄로 분리 기록**) + 상태 이력 저장

오류: `AUTHENTICATION_REQUIRED`(401), `FORBIDDEN_NOT_OWNER`(403), `RESERVATION_NOT_FOUND`(404), `RESERVATION_STATE_CONFLICT`(409)

> 취소와 환불이 같은 DB의 한 트랜잭션이므로 "취소는 됐는데 환불은 실패"하는 중간 상태가 구조적으로 없다. 별도의 환불 재처리 큐는 필요 없다.

### 5-5. 연장 — `POST /reservations/{id}/extend`

요청 바디: `{ "expectedEndTime": "2026-09-20T15:00:00", "newEndTime": "2026-09-20T16:00:00" }` (`expectedEndTime` = 클라이언트가 알고 있는 현재 종료 시각). 응답: **200**, `ApiResponse<ReservationResponse>`.

예약자 본인만. `now < end_time`인 경우만 가능(끝난 예약을 되살리는 것은 연장이 아니라 새 예약). 단일 트랜잭션 처리:
1. **spaceId 투영 조회**: 잠금 순서(`Space → Reservation`) 준수를 위해 `reservationId`로부터 `spaceId`를 먼저 투영 조회.
2. **Space 공유 잠금**: `spaceRepository.findByIdForShare(spaceId)`로 Space 공유 잠금 획득.
3. **Reservation 배타 잠금**: `reservationRepository.findByIdForUpdate(reservationId)`로 비관적 배타 잠금 획득.
4. **검증**: 소유권(`memberId`), 예약 상태(`CONFIRMED` 또는 `IN_USE`), 아직 종료되지 않음(`now < endTime`), `expectedEndTime` 일치 여부 확인. 운영시간 내, 30분 단위, 동일 날짜 등 연장 시간 정책 검증.
5. **슬롯 확보 및 크레딧 차감**: 추가 슬롯 INSERT(`secureSlots`, 충돌 시 409). 원 예약의 `price_per_slot_snapshot` 기준으로 추가 금액 크레딧 차감.
6. **조건부 UPDATE**: `extendIfEndTimeMatches`로 종료 시각과 총액 갱신. 영향 행 0이면 409 롤백.

오류: `VALIDATION_FAILED`(400), `INVALID_RESERVATION_TIME`(400), `AUTHENTICATION_REQUIRED`(401), `FORBIDDEN_NOT_OWNER`(403), `RESERVATION_NOT_FOUND`(404), `SPACE_NOT_FOUND`(404), `RESERVATION_SLOT_CONFLICT`(409), `RESERVATION_STATE_CONFLICT`(409), `RESERVATION_EXTEND_NOT_ALLOWED`(422), `INSUFFICIENT_BALANCE`(422)

> 실패해도 원 예약은 무손상(기존 슬롯을 건드리지 않음).

### 5-6. 체크아웃 — `POST /reservations/{id}/check-out`

바디 없음. 응답: **200**, `ApiResponse<ReservationResponse>` (`status: "COMPLETED"`).

예약자 본인만. `WHERE id=:id AND status='IN_USE'` 조건부 UPDATE → `COMPLETED`(`checked_out_at`=now). 같은 트랜잭션에서 활성 토큰 revoke + 상태 이력 저장. 슬롯 반환 없음, 환불 없음. **되돌릴 수 없다**(UI에 확인 다이얼로그).

> 체크인에는 별도 엔드포인트가 없다 — 최초 체크인은 §7 도어 토큰 검증의 부수 효과로 `CONFIRMED → IN_USE` 전이가 일어난다.

오류: `AUTHENTICATION_REQUIRED`(401), `FORBIDDEN_NOT_OWNER`(403), `RESERVATION_NOT_FOUND`(404), `RESERVATION_STATE_CONFLICT`(409)

## 6. [관리자] 예약 관리

- `GET /admin/reservations?page=&size=` (ADMIN): 취소 예약도 목록 포함. 응답 `ApiResponse<Page<AdminReservationResponse>>` (Spring Data `Page` 직렬화 형태. 원소: `{ reservationId, memberId, memberEmail, spaceId, spaceName, startTime, endTime, status, totalAmount, createdAt }`). ⚠️ **검색 조건 필터는 현재 컨트롤러에 파라미터가 매핑되어 있지 않음**(`@ParameterObject Pageable pageable`만 지원) — 프론트는 필터 UI를 비활성화하거나 전체 목록 페이징으로 연동
- `GET /admin/reservations/{reservationId}` (ADMIN): 관리자 예약 상세 조회. 응답 `ApiResponse<AdminReservationDetailResponse>` (목록 필드 + `pricePerSlotSnapshot`, `holdExpiresAt`, `checkedInAt`, `checkedOutAt`, `cancelledAt`, `statusHistory[]`, `accessLogs[]`). 오류: `AUTHENTICATION_REQUIRED`(401), `ACCESS_DENIED`(403), `RESERVATION_NOT_FOUND`(404)
- `POST /admin/reservations/{reservationId}/force-cancel` (ADMIN): 바디 `{ "reason": "..." }` 1~500자 필수, 응답 `ApiResponse<AdminReservationResponse>`. 취소 가능 상태는 `HELD`/`CONFIRMED`/`IN_USE`(그 외 종료 상태는 409). 조회 시점 상태 기준 조건부 UPDATE(`WHERE id=:id AND status=:조회 시점 상태`) → 같은 트랜잭션에서 상태 이력(사유 포함) + 슬롯 삭제 + 활성 토큰 revoke + **크레딧 환불(`CONFIRMED`/`IN_USE`는 `total_amount` 전액 `REFUND`, 위약금 없음 / `HELD`는 환불 없음)** + audit_log 기록. 오류: `VALIDATION_FAILED`(400), `AUTHENTICATION_REQUIRED`(401), `ACCESS_DENIED`(403), `RESERVATION_NOT_FOUND`(404), `RESERVATION_STATE_CONFLICT`(409). **관리자도 이 API 외의 경로로 타인 예약을 취소하거나 도어 토큰을 발급받을 수 없다** (핵심 차별점)

## 7. 출입 (Door Access)

> 현재 구현 경로(접두사 `/api/v1`): `POST /reservations/{id}/door-token`, `PATCH /reservations/{id}/access-token/revoke`, `GET /reservations/{id}/access-logs`, `POST /door-access/verify`. **4개 모두 로그인 필요**(verify도 서버가 로그인 회원이 예약자 본인인지 확인).

- `POST /reservations/{reservationId}/door-token` (예약자 본인만): **발급에는 시간 제한이 없다** — `CONFIRMED` 또는 `IN_USE`이고 `now < end_time`이면 예약 확정 직후부터 언제든 발급 가능(발급은 입장 권한이 아니라 신분증을 받는 것일 뿐, `start` 전엔 문이 열리지 않는다). 기존 활성 토큰은 먼저 폐기 후 재발급. 응답: 201 `ApiResponse<DoorAccessTokenResponse>` (`reservationId`, `token`, `issuedAt`). 오류: `AUTHENTICATION_REQUIRED`(401), `FORBIDDEN_NOT_OWNER`(403), `RESERVATION_NOT_FOUND`(404), `RESERVATION_STATE_CONFLICT`(409, 동시 발급 경합)
- `PATCH /reservations/{reservationId}/access-token/revoke` (예약자 본인만): 해당 예약에 발급된 활성 출입 토큰을 즉시 폐기. 응답: 200 `ApiResponse<Void>` (Java 상으로는 `data: null`이나, `@JsonInclude(NON_NULL)`로 인해 실제 HTTP JSON 직렬화 시 `"data"` 필드가 생략되어 `{ "status": "SUCCESS", "code": "OK", "message": "요청이 성공적으로 처리되었습니다." }` 형태로 반환됨). 오류: `AUTHENTICATION_REQUIRED`(401), `FORBIDDEN_NOT_OWNER`(403), `RESERVATION_NOT_FOUND`(404), `ACTIVE_ACCESS_TOKEN_NOT_FOUND`(404)
- `GET /reservations/{reservationId}/access-logs` (예약자 본인만): 해당 예약의 출입 기록 목록 조회. 응답: 200 `ApiResponse<List<DoorAccessLogResponse>>` (`accessLogId`, `attemptedAt`, `requestedSpaceName`, `result`, `reasonCode`). 오류: `AUTHENTICATION_REQUIRED`(401), `FORBIDDEN_NOT_OWNER`(403), `RESERVATION_NOT_FOUND`(404)
- `POST /door-access/verify` (인증 필요): 요청 바디 `{ spaceId, token }`. 응답: 200 `ApiResponse<DoorAccessVerifyResponse>` (`result: "ALLOW" | "DENY"`, `reasonCode`, `spaceName`, `firstCheckIn`, `attemptedAt`). 검증 순서:
  1. 해시로 활성 토큰 조회 (미존재 또는 폐기 토큰은 `TOKEN_NOT_FOUND` / `TOKEN_REVOKED`로 거절)
  2. 예약 상태(`CONFIRMED` 또는 `IN_USE`) 확인 (`RESERVATION_NOT_ACTIVE`), 요청 공간과 예약 공간 일치 확인 (`SPACE_MISMATCH`), 로그인 사용자와 예약자 일치 확인 (`MEMBER_MISMATCH`)
  3. 시간대 확인:
     - **최초 체크인**: `[start_time, start_time + 15분]` (시작 정각 허용, 15분 초과 시 거절)
     - **재입장**: `(checked_in_at, end_time)` (종료 정각은 거절)
     - 허용 시간 외 시 `OUTSIDE_ALLOWED_TIME`으로 거절
  4. 최초 체크인 성공 시 `CONFIRMED → IN_USE` 전이가 부수 효과로 발생
  5. 성공 및 거절 모두 `door_access_log`에 기록되며, 거절 시에도 HTTP 200과 함께 `result: "DENY"`로 반환
  - 오류: `VALIDATION_FAILED`(400), `AUTHENTICATION_REQUIRED`(401)

> **2026-09-15 정정** (`core-domain-decisions.md` §8): 기존 "발급 30분 전부터, 최초 체크인 시작 전후 30분" 규칙을 대체한다. 발급 시간 제한을 없애고, 최초 체크인은 앞 여유 0분·뒤 15분(`[start, start+15m]`)으로 확정. 15분 내 미체크인은 배치가 `NO_SHOW`로 전이시키며 슬롯은 반환되지만 환불은 없다.

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
  - 오류: `VALIDATION_FAILED`(400) (날짜 역전 `dateFrom > dateTo` 또는 파라미터 타입 오류), `AUTHENTICATION_REQUIRED`(401), `ACCESS_DENIED`(403)
  - 부수효과: 조회 자체는 감사 로그를 남기지 않음.

## 9. 문의 (Inquiry, Q&A) — MVP 3대 기능 외 추가 기능

> 문의 1건당 답변 1건(1:1)으로 단순화한다. 재질문/스레드형 재답변은 범위 밖.

### 9-1. 회원용

- `POST /inquiries` (인증 필요): 문의 생성. 요청 `{ title, content }` (title 최대 200자, content 최대 2000자, 필수). 응답: 201 `ApiResponse<InquiryResponse>`. 오류: `VALIDATION_FAILED`(400), `AUTHENTICATION_REQUIRED`(401)
- `GET /inquiries` (인증 필요): 본인 문의 목록. `page`, `size`. 응답: 200 `ApiResponse<PageResponse<InquiryResponse>>`. 오류: `AUTHENTICATION_REQUIRED`(401)
- `GET /inquiries/{inquiryId}` (인증 필요): 본인 문의 상세. 응답: 200 `ApiResponse<InquiryResponse>`. 오류: `AUTHENTICATION_REQUIRED`(401), `FORBIDDEN_NOT_OWNER`(403), `INQUIRY_NOT_FOUND`(404)
- `PATCH /inquiries/{inquiryId}` (인증 필요): 본인 문의 수정. `status`가 `WAITING`일 때만 가능. 요청 바디는 생성과 동일. 응답: 200 `ApiResponse<InquiryResponse>`. 오류: `VALIDATION_FAILED`(400), `AUTHENTICATION_REQUIRED`(401), `FORBIDDEN_NOT_OWNER`(403), `INQUIRY_NOT_FOUND`(404), `INQUIRY_ALREADY_ANSWERED`(422)

### 9-2. 관리자용

- `GET /admin/inquiries` (ADMIN): 전체 문의 목록. 쿼리 파라미터 `status` (`WAITING` / `ANSWERED`, 선택), `page`, `size`. 응답: 200 `ApiResponse<PageResponse<InquiryResponse>>`. 오류: `AUTHENTICATION_REQUIRED`(401), `ACCESS_DENIED`(403)
- `GET /admin/inquiries/{inquiryId}` (ADMIN): 문의 상세. 응답: 200 `ApiResponse<InquiryResponse>`. 오류: `AUTHENTICATION_REQUIRED`(401), `ACCESS_DENIED`(403), `INQUIRY_NOT_FOUND`(404)
- `POST /admin/inquiries/{inquiryId}/answer` (ADMIN): 답변 등록. 요청 `{ content }` (최대 2000자, 필수). 성공 시 `status`가 `ANSWERED`로 전이. 응답: 200 `ApiResponse<InquiryResponse>`. 오류: `VALIDATION_FAILED`(400), `AUTHENTICATION_REQUIRED`(401), `ACCESS_DENIED`(403), `INQUIRY_NOT_FOUND`(404)

### 응답 필드 (`InquiryResponse`)

`id`, `memberId`, `title`, `content`, `status`, `answerContent`, `answeredByMemberId`, `answeredAt`, `createdAt`

## 10. Enum 정의표

| 항목 | 값 |
| --- | --- |
| `member.role` | `USER`, `ADMIN` |
| `member.status` | `ACTIVE`, `SUSPENDED`, `WITHDRAWN` |
| `space.status` | `ACTIVE`, `INACTIVE` |
| `reservation.status` | `HELD`, `EXPIRED`, `CONFIRMED`, `IN_USE`, `COMPLETED`, `CANCELLED`, `NO_SHOW` |
| `credit_transaction.type` | `SIGNUP_GRANT`, `ADMIN_GRANT`, `RESERVATION_CHARGE`, `REFUND`, `PENALTY` |
| `door_access_log.result` | `ALLOW`, `DENY` |
| `audit_log.action` | `REGISTER_SPACE`, `MODIFY_SPACE`, `SUSPEND_MEMBER`, `REACTIVATE_MEMBER`, `FORCE_CANCEL_RESERVATION`, `GRANT_CREDIT` |
| `audit_log.target_type` | `SPACE`, `MEMBER`, `RESERVATION` |
| `inquiry.status` | `WAITING`, `ANSWERED` |

> ~~`payment.status`~~ 는 `payment` 테이블 삭제와 함께 제거됨. 결제 결과는 `credit_transaction`으로 표현한다.

## 11. 결정 필요 사항 (Pending Decisions)

코드 구현과 요구사항/문서 사이에 차이가 있어 제품 및 설계 결정이 필요한 항목들이다. 현재 구현과 목표 계약을 구분하여 관리한다:

1. **약관 동의 버전 (`termsVersion`) 수신 및 저장 여부**
   - **현재 구현**: 프론트엔드(`SpaceDetailPage.tsx`, `enums.ts`의 `TERMS_VERSION`)는 예약 생성 시 `termsVersion: 'v1.1'`(문자열)을 전송하고 있으나, 백엔드 DTO `ReservationCreateRequest` 및 DDL에는 필드가 없어 서버에서 역직렬화 시 버려지며 저장되지 않는다.
   - **결정 과제**: 예약 체결 시점의 이용약관 동의 증적을 위해 백엔드 DTO, Entity, DDL(`reservation.terms_version`)에 반영할 것인지, 아니면 프론트엔드의 전송을 제거하고 단순 클라이언트 UI 동의 체크로 만족할 것인지 결정 필요.
2. **멱등성 키 충돌 감지 및 실패 응답 캐싱/TTL 정책**
   - **현재 구현**: `POST /reservations/{id}/pay`에서 성공 응답(200 OK)만 `idempotency_key` 테이블에 저장되며, 실패 시에는 롤백되어 캐시되지 않는다. 또한 동일 키에 대해 다른 요청 본문이 전달되어도 충돌을 감지하는 로직(`IDEMPOTENCY_KEY_CONFLICT`)이 없다.
   - **결정 과제**: 동일 키에 다른 페이로드가 전달되었을 때 409 충돌을 발생시킬 것인지(요청 바디 해시 저장 필요), 실패 응답도 캐시하여 무조건 동일 결과를 재현할 것인지, 만료 기간(TTL) 및 테이블 정리 스케줄러를 도입할 것인지 결정 필요.
3. **정지 계정의 기존 Access Token 즉시 무효화 및 보호 API 차단**
   - **현재 구현**: `CustomAuthenticationFilter`는 JWT 서명과 만료 시각만 검증하며 회원 DB를 재조회하지 않는다. 비즈니스 서비스(`ReservationHoldService`, `DoorAccessVerificationService` 등)에서도 회원 `status == ACTIVE` 여부를 별도 확인하지 않는다. 따라서 회원이 정지되어도 기존 Access Token 만료 전까지는 예약 생성 및 도어 출입 시도가 기술적으로 통과된다(토큰 재발급 요청 시에만 `ACCOUNT_INACTIVE` 403 차단).
   - **결정 과제**: 정지 즉시 기존 토큰까지 완전 차단하려면 JWT 블랙리스트(Redis 등), 인증 필터 내 매 요청 DB/캐시 회원 상태 조회, 또는 서비스 레이어 진입 시 회원 상태 검증 로직 추가가 필요하며, 이를 별도 코드 과제로 추진할지 결정 필요.
4. **관리자 예약 목록 검색 조건 필터 지원**
   - **현재 구현**: 백엔드 `AdminReservationController`의 `GET /admin/reservations`는 페이징(`Pageable`)만 수신하며 `AdminReservationSearchCondition`은 빈 클래스 상태로 파라미터가 매핑되어 있지 않다.
   - **결정 과제**: 관리자 화면에서 상태/기간/공간 필터링이 필요하다면 Querydsl 동적 쿼리 및 컨트롤러 파라미터 바인딩을 구현하는 별도 과제로 진행할 것인지 결정 필요.
5. **노쇼 환불률**: 현재 확정값은 0%이며 강사 피드백에 따라 재검토 중(`docs/decisions/core-domain-decisions.md` §13). 변경 시 명세서와 크레딧 환급 로직 동기화 필요.
6. **HOLD 카운트다운 시간대 해석 차이 (프론트 별도 코드 과제)**
   - **현재 구현**: 백엔드는 오프셋 없는 ISO-8601 문자열(`yyyy-MM-dd'T'HH:mm:ss`)을 KST 기준으로 반환하고 클라이언트의 KST 해석을 기대하나, 프론트엔드 `HoldCountdown.tsx`는 `new Date(holdExpiresAt)`를 사용하여 **브라우저 로컬(현지) 시간대**로 파싱한다. KST가 아닌 브라우저 환경(예: UTC)에서는 만료 시점 계산에 9시간 등의 시간차가 발생할 수 있다.
   - **결정 과제**: 프론트엔드 `HoldCountdown.tsx`에서 타임존 오프셋(`+09:00`)을 명시적으로 결합하여 KST로 고정 파싱하도록 하는 수정은 문서 최신화 범위 밖의 **별도 프론트엔드 코드 과제**로 분리하여 추적한다.
