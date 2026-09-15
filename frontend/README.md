# Slot Key — Frontend

Vite + React SPA입니다. 화면 구성은 `slot-key-web` 프로토타입과 독립 결제 화면을 따르며,
디자인 시스템(`src/styles/tokens.css`, `src/styles/global.css`)도 프로토타입에서 이식했습니다.

## 실행

```bash
npm install
cp .env.example .env      # VITE_API_BASE_URL 을 로컬 백엔드 주소로 채웁니다
npm run dev
```

| 명령 | 설명 |
| --- | --- |
| `npm run dev` | 개발 서버 |
| `npm run build` | 배포 번들 생성 (`dist/`) |
| `npm run preview` | 빌드 결과 미리보기 |
| `npm run lint` | ESLint 검사 |

## 환경변수

| 이름 | 설명 |
| --- | --- |
| `VITE_API_BASE_URL` | 백엔드 API 기본 주소. 미설정 시 `http://localhost:8080/api/v1` |

실제 값이 담긴 `.env`는 커밋하지 않습니다. Vercel에서는 프로젝트 환경변수로 주입합니다.

## 구조

```
src
├── main.jsx / App.jsx   진입점 (BrowserRouter + AuthProvider)
├── router               routes.jsx, ProtectedRoute, AdminRoute
├── api                  client.js(fetch 래퍼) + 도메인별 호출 함수
├── context / hooks      AuthContext, useAuth, useApi, useSlotSelection, useIdempotencyKey
├── pages                사용자 · 관리자 · 독립 결제 화면
├── components           layout · common · space · reservation · access · member
├── constants            apiRoutes, routePaths, errorCodes, enums
├── utils                date, price, slot, storage, format
└── styles               tokens.css, global.css
```

## 알아둘 점

- **화면의 접근 제어는 편의입니다.** `ProtectedRoute`·`AdminRoute`가 메뉴와 화면을 가리지만,
  실제 권한 판단은 서버가 합니다. 화면에서 버튼을 숨겼다는 이유로 서버 검사를 생략하지 않습니다.
- **예약 가능 표시는 확정이 아닙니다.** 슬롯 조회 이후 다른 사용자가 먼저 예약할 수 있으므로,
  결제 요청이 `RESERVATION_SLOT_CONFLICT`를 받으면 슬롯 현황을 다시 불러옵니다.
- **예약과 결제는 분리됩니다.** 예약 생성은 10분짜리 `HELD` 상태를 만들고
  `/reservations/:reservationId/payment`에서 결제해야 `CONFIRMED`가 됩니다.
- **결제 요청은 `Idempotency-Key`를 보냅니다.** `useIdempotencyKey`가 만든 UUID를 같은 결제 시도
  동안 유지해야 네트워크 재시도에서 크레딧이 중복 차감되지 않습니다.
- **출입 키 원문은 발급 응답에서 한 번만 내려옵니다.** 서버에는 해시만 저장되어 재조회할 수 없습니다.
- **BrowserRouter를 사용하므로 SPA 리라이트가 필요합니다.** Vercel 설정은 `vercel.json`에 있습니다.

프론트엔드가 요구하는 결제 API와 경로 기준은
[../docs/FRONTEND2_PAYMENT_API.md](../docs/FRONTEND2_PAYMENT_API.md)를 참고합니다.
