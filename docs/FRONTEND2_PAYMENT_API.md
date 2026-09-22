# frontend 결제 API 계약

## 기준 경로

frontend와 백엔드의 정식 API prefix는 **`/api/v1`** 로 통일한다.

- 로컬 기본 URL: `http://localhost:8080/api/v1`
- 프론트엔드 환경변수: `NEXT_PUBLIC_API_BASE_URL`(기본값 `/api/v1`) *(2026-09-22 정정 — Next.js App Router 전환 이후 Vite 시절 변수명이 남아있던 것을 수정)*
- 배포 환경에 따라 API로 가는 경로가 다르다: 로컬/Next.js 서버가 있는 환경은 `next.config.ts`의 `rewrites()`(`BACKEND_ORIGIN`)가 프록시하고, 정적 export(S3+CloudFront) 배포는 CloudFront의 `/api/*` 동작이 EC2로 직접 넘긴다.
- 각 API 모듈에는 `/reservations`처럼 prefix를 제외한 상대 경로만 둔다.
- 공간 리소스는 단수형 `/space`가 아니라 복수형 `/spaces`를 사용한다.
- 프록시를 쓰더라도 브라우저가 호출하는 최종 공간 경로는 `/api/v1/spaces/...`가 되게 한다.

즉, frontend에서 `/api`, `/api/v1`, `/api/v1/space`를 동시에 지원하는 식으로 충돌을 숨기지 않는다.
배포 전 백엔드의 context path 또는 controller 공통 mapping, API 문서, 프론트 환경변수를
모두 `/api/v1`과 `/spaces` 기준으로 맞춘다.

## 예약 생성(HOLD)

`POST /api/v1/reservations`

예약 생성은 결제를 완료하지 않고 10분간 슬롯을 확보한다.

```json
{
  "spaceId": 12,
  "date": "2026-09-21",
  "startTime": "16:00",
  "endTime": "18:00",
  "termsVersion": "2026-09-01"
}
```

`201 Created` 응답에는 결제 화면을 구성할 수 있는 스냅샷을 포함한다.

```json
{
  "reservationId": 301,
  "status": "HELD",
  "spaceId": 12,
  "spaceName": "판교 테크타워 회의실 A",
  "spaceLocation": "3F",
  "spaceImagePath": "/images/meeting-a.webp",
  "spaceType": "MEETING_ROOM",
  "date": "2026-09-21",
  "startTime": "16:00",
  "endTime": "18:00",
  "partySize": null,
  "slotCount": 4,
  "pricePerSlotSnapshot": 10000,
  "totalAmount": 40000,
  "spaceVersion": 4,
  "holdExpiresAt": "2026-09-21T15:10:00+09:00"
}
```

## 결제 화면 재조회

`GET /api/v1/reservations/{reservationId}`

페이지 새로고침과 직접 접근을 위해 위 스냅샷을 반환한다. 소유자만 조회할 수 있다. 상태가
`HELD`가 아니면 프론트엔드는 예약 상세 페이지로 이동한다. 남은 시간은 서버의
`holdExpiresAt`으로 표시하며, 실제 만료 여부는 항상 서버가 최종 판단한다.

## 결제 확정

`POST /api/v1/reservations/{reservationId}/pay`

헤더:

```http
Authorization: Bearer <access-token>
Idempotency-Key: <UUID>
Content-Type: application/json
```

본문:

```json
{ "spaceVersion": 4 }
```

성공 시 `200 OK`와 `status: "CONFIRMED"`인 예약 상세를 반환한다. 서버는 한 트랜잭션에서
예약 소유자, `HELD` 상태, 만료 시각, 공간 버전, 잔액을 검증하고 크레딧 차감과 상태 전이를
완료해야 한다. 같은 `Idempotency-Key`의 재요청에는 최초 결과를 재사용해 중복 차감을 막는다.

주요 오류:

| HTTP | code | 처리 |
| --- | --- | --- |
| 401 | `UNAUTHORIZED` | 로그인 화면 또는 토큰 갱신 |
| 403 | `RESERVATION_ACCESS_DENIED` | 소유권 오류 표시 |
| 404 | `RESERVATION_NOT_FOUND` | 예약 없음 표시 |
| 409 | `RESERVATION_STATE_CONFLICT` | 상세 재조회 |
| 409 | `RESERVATION_HOLD_EXPIRED` | 결제 중단 후 새 예약 안내 |
| 409 | `SPACE_VERSION_MISMATCH` | 공간/슬롯 재조회 |
| 422 | `INSUFFICIENT_BALANCE` | 잔액 부족 표시 |

## 체크아웃

`POST /api/v1/reservations/{reservationId}/check-out`

성공 시에만 확인 창을 닫는다. 실패 응답은 기존 창 안에 표시하여 사용자가 오류를 확인하고
재시도할 수 있게 한다. 프론트가 오류를 삼키더라도 성공으로 해석해서는 안 된다.

## 금액과 환불 경계

금액 계산과 환불액은 서버가 정수 크레딧 단위로 확정한다. 프론트의 남은 분 표시는
`Math.floor()`를 사용해 59분 59초를 60분으로 올려 표시하지 않는다. 더 안전하게는 서버가
환불 예상액과 정책 구간을 함께 반환하고, 클라이언트는 그 값을 표시만 하는 방식이다.
