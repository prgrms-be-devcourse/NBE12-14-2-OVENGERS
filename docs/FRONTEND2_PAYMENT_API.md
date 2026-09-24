# frontend 결제 API 계약

> **2026-09-24 갱신 (대단위 1)**: 프론트엔드-백엔드 간 실제 HTTP 계약 및 DTO 구조 동기화 — `ApiResponse` 공통 래퍼, 실제 수신 4개 필드와 프론트 `termsVersion` 구분, 예약 상세 조회 시 프론트엔드의 공간 상세 추가 조회(`getSpace`) 합성 구조, 실존 `ErrorCode` 반영.

## 기준 경로

frontend와 백엔드의 정식 API prefix는 **`/api/v1`** 로 통일한다.

- 로컬 기본 URL: `http://localhost:8080/api/v1`
- 프론트엔드 환경변수: `NEXT_PUBLIC_API_BASE_URL`(기본값 `/api/v1`)
- 배포 환경에 따라 API로 가는 경로가 다르다: 로컬/Next.js 서버가 있는 환경은 `next.config.ts`의 `rewrites()`(`BACKEND_ORIGIN`)가 프록시하고, 정적 export(S3+CloudFront) 배포는 CloudFront의 `/api/*` 동작이 EC2로 직접 넘긴다.
- 각 API 모듈에는 `/reservations`처럼 prefix를 제외한 상대 경로만 둔다.
- 공간 리소스는 단수형 `/space`가 아니라 복수형 `/spaces`를 사용한다.
- 프록시를 쓰더라도 브라우저가 호출하는 최종 공간 경로는 `/api/v1/spaces/...`가 되게 한다.

즉, frontend에서 `/api`, `/api/v1`, `/api/v1/space`를 동시에 지원하는 식으로 충돌을 숨기지 않는다.
배포 전 백엔드의 context path 또는 controller 공통 mapping, API 문서, 프론트 환경변수를
모두 `/api/v1`과 `/spaces` 기준으로 맞춘다.

## 예약 생성(HOLD)

`POST /api/v1/reservations`

예약 생성은 결제를 완료하지 않고 10분간 슬롯을 임시 확보(`HELD`)한다.

### 1) 요청 페이로드

서버가 수신 및 유효성 검증하는 실제 필드는 아래 4개이다(`ReservationCreateRequest`):

```json
{
  "spaceId": 12,
  "date": "2026-09-21",
  "startTime": "16:00:00",
  "endTime": "18:00:00"
}
```

> ⚠️ **`termsVersion` 전송과 서버 처리**: 현재 프론트엔드(`SpaceDetailPage.tsx`, `enums.ts`의 `TERMS_VERSION`)는 약관 동의 버전(`termsVersion: 'v1.1'`)을 함께 전송하고 있으나, 백엔드 DTO 및 DB 엔티티에는 해당 필드가 없어 역직렬화 시 무시되며 저장되지 않는다. 이는 **결정 필요** 항목으로 관리 중이다.

### 2) 응답 페이로드 (`201 Created`)

성공 응답은 공통 `ApiResponse<ReservationResponse>` 래퍼에 담겨 반환된다:

```json
{
  "status": "SUCCESS",
  "code": "OK",
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "reservationId": 301,
    "spaceId": 12,
    "startTime": "2026-09-21T16:00:00",
    "endTime": "2026-09-21T18:00:00",
    "status": "HELD",
    "pricePerSlotSnapshot": 10000,
    "totalAmount": 40000,
    "holdExpiresAt": "2026-09-21T15:10:00",
    "checkedInAt": null,
    "checkedOutAt": null,
    "cancelledAt": null,
    "spaceVersion": 4,
    "refundAmount": null,
    "penaltyAmount": null,
    "createdAt": "2026-09-21T15:00:00"
  }
}
```

- `data.spaceVersion`: 결제 확정 호출(`POST .../pay`) 시 검증용으로 사용할 공간의 비즈니스 버전이다.
- `data.holdExpiresAt`: HOLD 만료 시각(서버 KST 기준, 오프셋 없음). 프론트엔드는 이 시각을 기준으로 카운트다운 타이머를 렌더링한다. *(주의: 현재 프론트 `HoldCountdown.tsx`는 `new Date(holdExpiresAt)`로 브라우저 로컬 시간대로 직접 파싱하고 있으며, KST 고정 파싱은 별도 프론트 코드 과제로 추적한다)*.
- **주의**: 서버 응답에는 `spaceName`, `spaceLocation`, `spaceImagePath` 등의 공간 정보가 포함되지 않는다. 프론트엔드는 결제 화면 마운트 시 `getMyReservation`을 통해 항상 공간 상세(`GET /api/v1/spaces/{spaceId}`)를 1차 호출하여 공간 정보를 화면 모델로 합성한다(이전 화면 캐시를 사용하지 않음).

## 결제 화면 구성 및 재조회

`GET /api/v1/reservations/{reservationId}`

페이지 새로고침이나 직접 URL 접근 시 결제 화면 정보를 복원하기 위해 호출한다. 소유자만 조회할 수 있다.

1. **예약 상세 응답 (`ReservationDetailResponse`)**:
   - `reservationId`, `spaceId`, `startTime`, `endTime`, `status`, `pricePerSlotSnapshot`, `totalAmount`, `holdExpiresAt`, `statusHistory` 등을 반환한다.
   - **서버 응답에 없는 필드**: `spaceVersion`과 공간 정보(`spaceName`, `spaceLocation`, `spaceImagePath`)는 반환되지 않는다.
2. **프론트엔드 합성 흐름 (`frontend/src/api/reservationApi.ts`의 `getMyReservation`)**:
   - 프론트엔드는 예약 상세 조회 후, 응답의 `spaceId`로 `GET /api/v1/spaces/{spaceId}`를 1차 호출하여 공간명, 사진, 위치 등을 화면 모델로 합성한다(반환 객체에는 `space.version`이 포함되지 않음).
   - 상태가 `HELD`가 아니면 프론트엔드는 예약 상세 또는 목록 페이지로 이동한다.
3. **결제 화면 재진입 시 `spaceVersion` 처리 (`PaymentPage.tsx:65-68`)**:
   - URL 쿼리 파라미터에 `spaceVersion`이 있으면 해당 값을 숫자로 파싱해 사용한다.
   - 새로고침 등으로 쿼리 파라미터가 없는 경우, 합성된 예약 객체에는 `version`이 없으므로 **결제 버튼 실행 시점(`pay`)에 `(await getSpace(reservation.spaceId)).version`을 별도로 다시 호출(2차 호출)**하여 최신 공간 버전을 획득해 pay 요청 바디에 전송한다.

## 결제 확정

`POST /api/v1/reservations/{reservationId}/pay`

헤더:

```http
Authorization: Bearer <access-token>
Idempotency-Key: <UUID>
Content-Type: application/json
```

본문 (`ReservationPayRequest`):

```json
{
  "spaceVersion": 4
}
```

성공 시 `200 OK`와 `status: "CONFIRMED"`인 `ApiResponse<ReservationResponse>`를 반환한다. 서버는 단일 트랜잭션에서
예약 소유권 선검증(`FORBIDDEN_NOT_OWNER`), Space 공유 락 및 버전 검증(`SPACE_VERSION_MISMATCH`), 크레딧 차감(`INSUFFICIENT_BALANCE`),
`HELD` 만료 여부 조건부 전이(`RESERVATION_STATE_CONFLICT`)를 원자적으로 수행한다.
동일한 `Idempotency-Key`의 성공 재요청에는 최초 캐시 결과를 그대로 반환하여 중복 차감을 원천 방지한다.

### 주요 오류 코드와 프론트엔드 처리

| HTTP | ErrorCode | 설명 및 현재 프론트엔드 처리 (PaymentPage.tsx) |
| --- | --- | --- |
| 400 | `IDEMPOTENCY_KEY_REQUIRED` | `Idempotency-Key` 헤더 누락 시 발생 |
| 400 | `VALIDATION_FAILED` | `spaceVersion` 누락 또는 잘못된 요청 형식 |
| 401 | `AUTHENTICATION_REQUIRED` | Access Token 누락 또는 만료 (로그인 페이지 이동 또는 토큰 갱신) |
| 403 | `FORBIDDEN_NOT_OWNER` | 예약 소유자가 아닌 회원의 결제 시도 |
| 404 | `RESERVATION_NOT_FOUND` | 유효하지 않은 `reservationId` |
| 404 | `SPACE_NOT_FOUND` | 연계된 공간을 찾을 수 없음 |
| 409 | `RESERVATION_STATE_CONFLICT` | **HOLD 만료** 또는 이미 확정/취소된 예약.<br>• *현재 UI*: 공통 `<ErrorMessage>`로 서버 오류 문구 표시. (타이머 로컬 만료 시 "결제 대기 시간이 끝났습니다. 새로 예약해 주세요." 배너 및 결제 버튼 비활성화)<br>• *(권장 UX / 추후 과제)*: 만료 전용 다이얼로그 모달 노출 후 새 예약 페이지로 유도 |
| 409 | `SPACE_VERSION_MISMATCH` | 결제 대기 중 공간 정보(가격 등)가 변경됨.<br>• *현재 UI*: 공통 `<ErrorMessage>`로 노출.<br>• *(권장 UX / 추후 과제)*: 공간 정보 변경 안내 모달 후 공간 상세 재조회 이동 |
| 422 | `INSUFFICIENT_BALANCE` | 크레딧 잔액 부족.<br>• *현재 UI*: 화면 진입 시 잔액 부족이면 "크레딧 잔액이 부족합니다." 배너 표시 및 결제 버튼 비활성화, 실패 시 공통 `<ErrorMessage>` 노출.<br>• *(권장 UX / 추후 과제)*: 크레딧 충전 페이지 이동 안내 다이얼로그 (현재 프론트에 충전 경로 없음) |

> 💡 **주의**: 백엔드 `ErrorCode`에는 `UNAUTHORIZED`, `RESERVATION_ACCESS_DENIED`, `RESERVATION_HOLD_EXPIRED`라는 코드가 존재하지 않는다. 인증 실패는 `AUTHENTICATION_REQUIRED`(401), 소유권 불일치는 `FORBIDDEN_NOT_OWNER`(403), HOLD 만료는 `RESERVATION_STATE_CONFLICT`(409)로 응답되므로 프론트엔드 에러 핸들러는 이 실제 코드를 기준으로 분기해야 한다.

## 체크아웃

`POST /api/v1/reservations/{reservationId}/check-out`

- 바디 없음. 성공 시 `200 OK`와 `status: "COMPLETED"` 응답 반환.
- 성공 시에만 확인 모달을 닫는다. 실패 응답은 기존 창 안에 오류 메시지를 표시하여 사용자가 확인하고 재시도할 수 있게 한다. 프론트가 오류를 삼켜 성공으로 해석해서는 안 된다.

## 금액과 환불 경계

- 금액 계산과 환불액은 서버가 정수 크레딧 단위로 확정한다.
- 프론트의 남은 시간 표시는 `Math.floor()`를 사용해 59분 59초를 60분으로 올려 표시하지 않는다.
- 시간 필드는 타임존 오프셋 표기가 없는 ISO-8601 문자열(`YYYY-MM-DDTHH:mm:ss`)로 전달되므로, 프론트엔드는 이를 한국 표준시(KST)로 정확히 파싱하는 것이 설계 목표이다. (단, 현재 `HoldCountdown.tsx`는 브라우저 로컬 시간대로 파싱 중이므로 향후 `+09:00` 결합 등 KST 고정 파싱 보완을 별도 프론트 코드 과제로 진행한다).
- 취소 시 환불 규정: 시작 1시간 전까지 100% 환불, 1시간 전~시작 전 50% 환불(위약금 50%), 시작 이후 취소 불가(체크아웃 종료).
- 서버 취소 응답(`POST .../cancel`)에는 `refundAmount`와 `penaltyAmount`가 계산되어 포함된다.
