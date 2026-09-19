# Slot Key — Frontend

Next.js(App Router) + TypeScript 앱입니다. 화면 구성은 `slot-key-web` 프로토타입과 독립 결제
화면을 따르며, 디자인 시스템(`src/styles/tokens.css`, `src/styles/global.css`)도 프로토타입에서
이식했습니다.

## 실행

```bash
npm install
npm run dev
```

로컬 백엔드(`localhost:8080`)에 붙일 때는 `.env.local` 에 아래 한 줄만 둡니다.

```bash
BACKEND_ORIGIN=http://localhost:8080
```

| 명령 | 설명 |
| --- | --- |
| `npm run dev` | 개발 서버 |
| `npm run build` | 프로덕션 빌드 (`.next/`) |
| `npm run start` | 빌드 결과 실행 |
| `npm run lint` | ESLint 검사 (`next/core-web-vitals` + `next/typescript`) |
| `npx tsc --noEmit` | 타입 검사만 (빌드 없이) |

## 환경변수

| 이름 | 쓰이는 곳 | 설명 |
| --- | --- | --- |
| `NEXT_PUBLIC_API_BASE_URL` | 브라우저 | API 기본 주소. 미설정 시 `/api/v1` (같은 오리진 → rewrite 경유) |
| `BACKEND_ORIGIN` | 서버(rewrite) | `/api/v1/*` 를 넘길 백엔드 오리진. 미설정 시 `http://localhost:8080` |

브라우저는 언제나 같은 오리진의 `/api/v1` 로 요청하고, 실제 백엔드로 넘기는 일은
`next.config.mjs` 의 rewrite 가 맡습니다. 그래서 CORS 설정과 혼합 콘텐츠(HTTPS 페이지 → HTTP API)
문제를 함께 피합니다. (예전 `vercel.json` 의 rewrites 를 옮겨온 것입니다.)

실제 값이 담긴 `.env.local` 은 커밋하지 않습니다. Vercel 에서는 프로젝트 환경변수로 주입합니다.

## 배포 (Vercel)

`vercel.json` 은 프레임워크를 `nextjs` 로 고정하는 용도만 남았습니다. rewrite 는
`next.config.mjs` 로 옮겼으므로 여기서 찾지 않습니다.

이 파일이 필요한 이유는 Vite 시절 대시보드 설정이 남아 있기 때문입니다.
Framework Preset 과 Output Directory 는 서로 다른 설정이라 하나만 고쳐서는 안 되고,
Output Directory 가 `dist` 로 남아 있으면 빌드가 성공해도 Vercel 이 `.next` 대신
`dist` 를 찾다가 배포가 실패합니다. `vercel.json` 의 설정이 대시보드보다 우선하므로
둘 다 여기서 못박아 둡니다.

Root Directory 는 `frontend` 로 잡혀 있어야 하며, 이 값은 `vercel.json` 으로 덮을 수
없으니 대시보드에서 확인합니다.

## 구조

```
src
├── types/api.ts         서버 응답·요청 타입. 서버 DTO 가 바뀌면 여기부터 고칩니다
├── app                  App Router 라우트. page/layout 은 얇게 두고 metadata 만 선언
│   ├── layout.jsx       <html>/<body>, 공통 metadata, Providers
│   ├── icon.svg         파비콘 (apple-icon.png 과 같은 브랜드 마크)
│   ├── (user)           사용자 화면 그룹 — UserShell(헤더·푸터)
│   └── admin            관리자 화면 — RequireAdmin + AdminShell
├── views                실제 화면 컴포넌트('use client'). app 의 page 가 얇게 감쌉니다
├── api                  client.js(fetch 래퍼) + 도메인별 호출 함수
├── context / hooks      AuthContext, useAuth, useApi, useSlotSelection, useIdempotencyKey
├── components           auth(접근 제어) · layout · brand · common · space · reservation · access · member
├── constants            apiRoutes, routePaths, errorCodes, enums
├── utils                date, price, slot, storage, format
└── styles               tokens.css, global.css
```

## 알아둘 점

- **타입의 기준은 `src/types/api.ts` 입니다.** 서버 DTO 가 바뀌면 이 파일을 먼저 고치고,
  컴파일 오류가 가리키는 화면을 따라가면 고쳐야 할 곳이 빠짐없이 드러납니다.
- **`tsconfig.json` 은 `strict: true` 입니다.** `any` 로 막지 말고 타입을 좁히거나
  `src/types/api.ts` 에 모양을 추가하는 쪽으로 해결합니다.
- **화면의 접근 제어는 편의입니다.** `RequireAuth`·`RequireAdmin` 이 메뉴와 화면을 가리지만,
  실제 권한 판단은 서버가 합니다. 화면에서 버튼을 숨겼다는 이유로 서버 검사를 생략하지 않습니다.
- **인증은 클라이언트에서만 이루어집니다.** 토큰을 `localStorage` 에 두므로 화면은 모두
  클라이언트 컴포넌트이고, 서버 컴포넌트에서 사용자별 데이터를 미리 가져오지 않습니다.
- **App Router 에는 라우트 state 가 없습니다.** react-router 의 `navigate(..., { state })` 로
  넘기던 값은 쿼리스트링으로 바꿨습니다. 로그인 복귀 경로는 `?redirect=`,
  결제 화면의 공간 버전은 `?spaceVersion=` 입니다.
- **예약 가능 표시는 확정이 아닙니다.** 슬롯 조회 이후 다른 사용자가 먼저 예약할 수 있으므로,
  요청이 `RESERVATION_SLOT_CONFLICT` 를 받으면 슬롯 현황을 다시 불러옵니다.
- **예약과 결제는 분리됩니다.** 예약 생성은 10분짜리 `HELD` 상태를 만들고
  `/reservations/[reservationId]/payment` 에서 결제해야 `CONFIRMED` 가 됩니다.
- **결제 요청은 `Idempotency-Key` 를 보냅니다.** `useIdempotencyKey` 가 만든 UUID 를 같은 결제 시도
  동안 유지해야 네트워크 재시도에서 크레딧이 중복 차감되지 않습니다.
- **출입 키 원문은 발급 응답에서 한 번만 내려옵니다.** 서버에는 해시만 저장되어 재조회할 수 없습니다.

프론트엔드가 요구하는 결제 API와 경로 기준은
[../docs/FRONTEND2_PAYMENT_API.md](../docs/FRONTEND2_PAYMENT_API.md)를 참고합니다.
