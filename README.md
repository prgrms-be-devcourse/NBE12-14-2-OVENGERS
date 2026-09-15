# 🔑 Slot Key

> 시간을 Slot으로 나누고, 예약한 Slot이 하나의 Key가 된다.
>

**Slot Key**는 회의실·공유오피스를 30분 단위로 예약하고, 예약 상태와 이용 시간에 따라 출입 권한을 제공하는 서비스입니다.

예약은 슬롯 확보(HOLD)와 결제 확인 두 단계로 처리하고, 출입 요청마다 **회원·예약·출입 키의 현재 상태와 이용 시간**을 확인합니다. 이번 프로젝트는 동시 예약 방지, 트랜잭션 정합성, 역할·소유권·상태·시간을 조합한 인가를 구현하고 검증하는 데 집중합니다. *(2026-09-15: `docs/core-domain-decisions.md` 확정본에 따라 예약 흐름을 2단계로 갱신)*

> 현재 개발 중인 프로젝트입니다. 구현 현황과 테스트 결과는 개발 진행에 따라 갱신합니다.
>

| 항목 | 내용 |
| --- | --- |
| 팀 | 05팀 · 오벤져스 |
| 과정 | 백엔드 데브코스 12기 14회차 |
| 개발 기간 | 2026.09.14 ~ 2026.10.01 |
| 서비스 URL | 배포 후 추가 |
| API 문서 | Swagger 배포 후 추가 |
| 시연 영상 | 촬영 후 추가 |

## 📋 목차

1. 🎯 프로젝트 목표
2. 🧩 주요 기능
3. 🛠️ 기술 스택
4. 🏗️ 시스템 아키텍처
5. 🗂️ 데이터 모델
6. 📐 핵심 설계
7. 🔐 인증 및 인가
8. 🧪 테스트 전략
9. 🚀 프로젝트 구조 및 실행
10. 🤝 협업 규칙
11. 👥 팀원 및 역할
12. 📝 기술적 의사결정

## 🎯 프로젝트 목표

### 동시 예약 방지

예약 가능 시간을 조회한 이후 다른 사용자가 먼저 예약할 수 있습니다. 화면의 예약 가능 여부와 별개로, 서버가 예약을 확정하는 시점에 동일 공간의 겹치는 예약을 차단하도록 설계합니다.

### 예약과 결제의 일관성

예약 확정 과정에서 일부 작업만 성공하면 예약과 크레딧 잔액이 달라질 수 있습니다. 슬롯 확보(HOLD 생성)와 결제 확정(크레딧 차감 + `CONFIRMED` 전이)을 각각 하나의 트랜잭션으로 관리하여 부분 실패 시 해당 단계의 변경 내용만 롤백하는 것을 목표로 합니다. 결제 실패 시에도 예약 자체는 `HOLD`로 남아 만료 전까지 재시도할 수 있습니다.

### 예약 상태와 출입 권한의 일치

출입 키가 남아 있더라도 예약이 취소되거나 이용 시간이 종료되면 출입할 수 없어야 합니다. 계정이 정지된 경우에도 기존 인증 정보만으로 보호 기능과 출입 기능을 계속 이용할 수 없도록 합니다.

### 역할과 소유권의 분리

관리자는 공간과 회원을 관리하고 전체 예약을 조회합니다. 관리자라는 이유만으로 다른 회원의 예약을 취소하거나 출입 키를 발급받을 수 없도록 작업별 권한을 구분합니다.

## 🧩 주요 기능

| 영역 | 기능 |
| --- | --- |
| 회원 | 이메일·비밀번호 기반 회원가입 및 로그인 |
| 공간 | 공간 목록·상세 조회, 날짜별 예약 가능 시간 확인 |
| 예약 | 30분 단위 예약(HOLD → 결제 확정), 본인 예약 조회·취소·연장 |
| 출입 | 예약 기반 출입 키 발급·재발급, 모의 출입 검증(최초 체크인 포함), 체크아웃 |
| 관리자 | 공간 등록·수정, 전체 예약 조회, 일반 회원 정지·복구 |
| 이력 | 예약 상태 변경, 출입 시도, 관리자 작업 기록 |

### 서비스 이용 흐름

```
	1. USER
	-> 회원가입 및 로그인
  -> 공간, 날짜, 이용 시간 선택
  -> 요금 확인 및 약관 동의
  -> 예약 생성(슬롯 확보, HOLD)
  -> 결제 확인 및 확정(CONFIRMED)
  -> 출입 키 발급
  -> 모의 출입 검증
  -> 이용 완료
  
  2. ADMIN
   -> 관리자 flow는 추후 작성
```

### 구현 범위에서 제외한 기능

- 실제 PG(Payment Gateway) 결제 및 스마트락 연동 — 크레딧 잔액 차감으로 대체, 셀프 충전도 범위 밖
- 비동기 PG 콜백 (크레딧 차감은 동기적으로 즉시 처리)
- 팀 리더의 대리 예약과 대리 취소
- 관리자에 의한 타인 예약 취소 및 출입 키 발급
- 회원 등급, 멤버십, 할인 등 동적 요금
- **예약 시간대 이동(변경)** 및 자동 재예약 — 연장(기존 슬롯 유지, 종료 시각만 늘리는 것)은 별개이며 제외 대상이 아닙니다
- 예약 대기(대기번호)·승격, 관리자 점검 시간 블록, 크레딧 회수, 출입 키 공유·동행자 초대
- 패스키 QR로 발급
- 소셜 로그인, 모바일 앱

예약 시간대 이동은 기존 예약을 취소한 뒤 새로운 예약을 생성하는 방식으로 처리합니다. *(2026-09-15: `docs/core-domain-decisions.md` §10 반영 — "결제 대기 상태" 제외 항목은 HOLD 도입으로 더 이상 사실이 아니므로 삭제, 연장 관련 제외 범위를 명확화)*

## 🛠️ 기술 스택

아래는 프로젝트에 적용할 기술입니다. 정확한 버전은 프로젝트 설정 파일을 기준으로 갱신합니다.

| 영역 | 기술 | 사용 목적 |
| --- | --- | --- |
| Backend | Spring Boot 3.x | REST API 및 애플리케이션 구성 |
| Security | Spring Security 6.x, JWT | 인증 및 API 접근 제어 |
| Persistence | Spring Data JPA | 데이터 접근과 트랜잭션 처리 |
| Database | MySQL | 회원·공간·예약·출입 이력 저장 |
| Test | JUnit 5, Testcontainers | 단위 테스트 및 MySQL 통합 테스트 |
| Frontend | React | 공간 예약 및 모의 출입 화면 |
| API Docs | Swagger UI | API 요청·응답 명세 공유 |
| CI/CD | GitHub Actions | 빌드 및 테스트 자동화 |
| Deployment | AWS EC2, Vercel | 백엔드·DB 및 프론트엔드 배포 |

백엔드 배포는 마일스톤 여부에 따라 Railway를 채택할 수 있습니다.

## 🏗️ 시스템 아키텍처

Slot Key는 하나의 Spring Boot 애플리케이션 안에서 인증, 공간, 예약, 출입, 관리자 기능을 도메인별로 분리하는 구조를 사용합니다.

```
flowchart TD
    USER["회원 / 관리자"] --> FE["React"]
    FE --> API["Spring Boot REST API"]

    API --> AUTH["인증 및 인가"]
    AUTH --> SPACE["공간"]
    AUTH --> RESERVATION["예약"]
    AUTH --> ACCESS["출입"]
    AUTH --> ADMIN["관리자"]

    RESERVATION --> CREDIT["크레딧 서비스(Mock 결제 = 잔액 차감/환급)"]
    SCHEDULER["HOLD 만료 / 노쇼 / 자동 퇴실 처리"] --> RESERVATION

    SPACE --> DB[("MySQL")]
    RESERVATION --> DB
    ACCESS --> DB
    ADMIN --> DB
```

- 예약 도메인은 예약 생성·취소·완료와 관련된 데이터 변경을 조율합니다.
- 출입 도메인은 회원, 예약, 출입 키와 이용 시간을 종합하여 출입 가능 여부를 판단합니다.
- 결제는 실제 금전 거래 없이 크레딧 잔액을 차감·환급하는 내부 모듈로 구현합니다(`payment` 테이블 없음, `credit_transaction` 원장으로 단일화).
- 스케줄러는 만료된 HOLD 정리, 노쇼 판정, 종료 시각이 지난 이용의 자동 퇴실(완료) 처리를 담당하되, 출입 요청에서도 현재 시각을 확인합니다.

## 🗂️ 데이터 모델

### 주요 엔터티

| 엔터티 | 역할 |
| --- | --- |
| `member` | 회원의 로그인 정보, 역할, 현재 계정 상태 관리 |
| `space` | 공간 정보, 운영시간, 수용 인원, 시간당 요금 관리 |
| `reservation` | 예약자, 공간, 이용 시간, 확정 금액, 예약 상태(HOLD 포함 7단계) 관리 |
| `credit_transaction` | 회원별 크레딧 지급·차감·환급 원장(부호 있는 금액, `payment` 대체) 관리 |
| `reservation_slot` | 예약이 점유한 30분 단위 슬롯과 중복 예약 방지 |
| `reservation_status_history` | 예약 생성·취소·완료에 따른 상태 전이 이력 관리 |
| `door_access_token` | 예약에 따른 출입 토큰 발급·폐기 이력 관리 |
| `door_access_log` | 모든 출입 허용·거절 결과와 사유 기록 |
| `refresh_token` | 로그인 연장 토큰의 발급, 만료, 폐기 관리 |
| `audit_log` | 공간 및 회원에 대한 관리자 작업 기록 |

```
erDiagram
    member ||--o{ reservation : owns
    member ||--o{ refresh_token : owns
    space ||--o{ reservation : receives

    reservation ||--o{ reservation_status_history : records
    reservation ||--o{ reservation_slot : occupies
    reservation ||--o{ door_access_token : issues
    reservation o|--o{ door_access_log : identifies
    reservation o|--o{ credit_transaction : charges

    member ||--o{ reservation_status_history : changes
    member ||--o{ door_access_log : attempts
    member ||--o{ credit_transaction : grants
    space ||--o{ door_access_log : receives
    member ||--o{ audit_log : performs
```

출입 토큰이 유효하지 않아 예약을 식별할 수 없는 경우를 고려해 `door_access_log.reservation_id`는 `NULL`을 허용합니다. `door_access_token`은 `active_reservation_id`(폐기되지 않은 토큰만 값을 가지는 생성 컬럼)에 `UNIQUE` 제약을 적용해, 예약당 활성 토큰이 최대 1개가 되도록 DB 레벨에서 강제합니다.

### 예약 시점 정보 보존

| 필드 | 목적 |
| --- | --- |
| `price_per_slot_snapshot` | 예약 확정 당시의 30분당 요금 보존 |
| `total_amount` | 확정된 총 결제 금액 보존 |
| `terms_version` | 예약 시 동의한 약관 버전 기록 |
| `created_at` | 예약 확정 시각 및 약관 동의 시점 기록 |
| `idempotency_key` | 동일한 예약 생성 요청의 중복 처리 방지 |

공간 요금이 변경되더라도 기존 예약의 `price_per_slot_snapshot`과 `total_amount`는 변경하지 않습니다.

> 상세 컬럼과 제약 조건은 최종 ERD 확정 후 문서 링크를 추가할 예정입니다.
>

## 📐 핵심 설계

### 예약 시간과 요금

- 예약은 같은 날짜의 공간 운영시간 안에서 생성합니다.
- 시작·종료 시각은 30분 단위로 제한합니다.
- 종료 시각은 시작 시각보다 늦어야 합니다.
- 과거 시간과 다른 예약이 점유한 시간은 예약할 수 없습니다.
- 모든 회원에게 공간별 고정 시간당 요금을 동일하게 적용합니다.
- 최종 결제 금액은 클라이언트가 아닌 서버에서 계산합니다.

```
총 결제 금액 = 예약 당시 30분당 요금(price_per_slot_snapshot) × 점유 슬롯 수

예시
30분당 요금 5,000원 × 슬롯 2개(60분 이용) = 10,000원
```

### 예약 생성

예약 생성은 다음 순서로 처리할 예정입니다.

```
[1단계] POST /reservations (결제 없음)
회원 상태 확인
  → 공간 상태 및 운영시간 검증
  → 요청 시간과 약관 동의 검증
  → 현재 요금 계산
  → 만료된 HOLD 정리 → 슬롯 확보(UNIQUE) → HOLD 생성

[2단계] POST /reservations/{id}/pay (Idempotency-Key 필수)
space.version 비교 → 크레딧 차감 → CONFIRMED 전이
```

각 단계는 자신의 트랜잭션 안에서만 원자적입니다. 1단계에서 슬롯 확보가 실패하면 전체 롤백되고, 2단계에서 크레딧 차감이 실패하면 결제만 롤백되어 예약은 `HOLD`로 남아 만료 전까지 재시도할 수 있습니다. *(2026-09-15: `docs/core-domain-decisions.md` §2 반영 — 기존 1단계 즉시결제 흐름을 대체)*

동시 예약 제어 방식은 실제 MySQL 환경에서 검증한 뒤 상세 설계와 테스트 결과를 추가할 예정입니다.

### 요청 멱등성

결제 확정 요청(`POST /reservations/{id}/pay`)에는 `Idempotency-Key`를 사용합니다. 슬롯만 확보하는 `POST /reservations`(HOLD 생성)는 결제가 없으므로 대상이 아닙니다.

- 동일 회원이 같은 키와 같은 요청을 다시 전송하면 중복 처리를 방지합니다.
- 같은 키로 다른 요청 내용을 보내면 충돌로 처리할 예정입니다.
- 서로 다른 키로 같은 시간을 요청하면 예약 동시성 규칙을 적용합니다.

멱등성은 동일 요청의 재전송을 처리하고, 예약 동시성 제어는 서로 다른 요청 사이의 시간 중복을 처리합니다.

### 예약 상태

```
stateDiagram-v2
    [*] --> HELD: 슬롯 확보
    HELD --> CONFIRMED: 결제 성공
    HELD --> EXPIRED: 10분 내 미결제
    CONFIRMED --> IN_USE: 최초 체크인
    CONFIRMED --> CANCELLED: 시작 전 본인 취소
    CONFIRMED --> NO_SHOW: 시작+15분 미체크인
    IN_USE --> COMPLETED: 체크아웃 또는 종료 시각 경과
```

- 슬롯 확보에 성공하면 `HELD`로 생성하고, 결제(크레딧 차감)에 성공하면 `CONFIRMED`로 전이합니다.
- `HELD`는 10분 내 결제하지 않으면 `EXPIRED`로 전이하고 슬롯을 반환합니다.
- 예약 시작 전의 `CONFIRMED` 예약만 취소할 수 있고(1시간 전까지 100%, 그 이후~시작 전 50% 환불), 시작 이후는 체크아웃으로만 종료합니다.
- 시작 후 15분까지 체크인하지 않으면 `NO_SHOW`로 전이하고 슬롯은 반환하되 환불은 없습니다.
- `EXPIRED`/`CANCELLED`/`COMPLETED`/`NO_SHOW`는 종단 상태이며 되돌아가지 않습니다.
- 실제 상태 변경에 성공한 경우에만 상태 이력을 저장합니다.

*(2026-09-15: `docs/core-domain-decisions.md` §3 반영 — 기존 3단계(`CONFIRMED`/`CANCELLED`/`COMPLETED`) 다이어그램을 7단계로 대체)*

### 출입 키

- 예약자 본인에게만 출입 키를 발급합니다.
- 회원이 `ACTIVE`이고 예약이 `CONFIRMED`인 경우 발급 가능하며, **발급 자체에는 시간 제한이 없습니다**(확정 직후~종료 전 언제든). 발급은 입장 권한이 아니라 신분증을 받는 것일 뿐, 시작 시각 전엔 문이 열리지 않습니다.
- 출입 키 원문은 발급 응답에서 한 번만 보여주고 서버에는 해시값만 저장합니다.
- 재발급 시 기존 활성 키를 폐기하도록 설계합니다.
- 예약 취소 직후 기존 출입 키를 사용할 수 없도록 합니다.
- 출입 시 회원, 예약자, 공간, 예약 상태, 키 상태와 현재 시각을 확인합니다. 최초 체크인 성공은 `CONFIRMED → IN_USE` 전이의 부수 효과입니다.
- 종료 시 체크아웃(`POST /reservations/{id}/check-out`)으로 마감합니다. 슬롯 반환·환불 없음, 되돌릴 수 없음.

최초 체크인 허용 구간은 `[시작 시각, 시작 시각 + 15분]`(앞 여유 0분), 재입장 허용 구간은 `(최초 체크인 시각, 종료 시각)`입니다. *(2026-09-15: `docs/core-domain-decisions.md` §8 확정 — 기존 "최종 API 명세 확정 후 반영" 상태에서 확정값으로 갱신)*

### 공간 운영 상태

| 상태 | 의미 |
| --- | --- |
| `ACTIVE` | 신규 예약 가능 |
| `INACTIVE` | 신규 예약 불가 |

공간을 `INACTIVE`로 변경해도 이미 확정된 예약은 유지합니다. 미래의 확정 예약과 충돌하는 운영시간 축소는 거절하도록 설계합니다.

## 🔐 인증 및 인가

JWT를 검증한 뒤 DB에서 현재 회원 상태와 역할을 확인합니다. 예약 관련 작업에서는 예약 소유권과 상태를 추가로 검사합니다.

| 작업 | 비회원 | 회원 | 플랫폼 관리자 |
| --- | --- | --- | --- |
| 공간 조회 | 가능 | 가능 | 가능 |
| 본인 예약 생성·조회·취소 | 불가 | 가능 | 동일한 예약자 정책 적용 |
| 타인 예약 조회 | 불가 | 불가 | 관리자 조회 API에서 가능 |
| 타인 예약 취소 | 불가 | 불가 | 불가 |
| 타인 출입 키 발급 | 불가 | 불가 | 불가 |
| 공간 등록·수정 | 불가 | 불가 | 가능 |
| 일반 회원 정지·복구 | 불가 | 불가 | 가능 |

### 보안 원칙

- 계정 정지는 기존 예약과 결제를 자동 취소하지 않습니다.
- 정지 처리 후 기존 Access Token으로 보호 API를 호출해도 거절하도록 합니다.
- 회원가입 요청으로 관리자 역할을 지정할 수 없도록 합니다.
- 일반 회원의 타인 예약 접근은 자원 노출을 줄이기 위해 `404`로 처리할 예정입니다.
- 관리자 계정 정지와 역할 변경은 MVP 범위에서 제외합니다.
- 화면에서 버튼을 숨기는 것과 별개로 서버에서 권한을 검사합니다.
- 비밀번호는 BCrypt로 해시하여 저장합니다.
- 비밀번호, JWT, Authorization 헤더, 출입 키 원문은 로그에 기록하지 않습니다.
- 배포 자격증명과 서명 키는 환경변수 또는 GitHub Secrets로 관리합니다.

## 📡 API 개요

**Base URL:** `/api/v1`

인증이 필요한 API는 다음 헤더를 사용합니다. 토큰 재발급·로그아웃은 요청 본문의 Refresh Token으로 처리합니다.

```
Authorization: Bearer {accessToken}
```

아래 Endpoint는 Base URL을 제외한 경로입니다.

| 영역 | Method | Endpoint | 설명 |
| --- | --- | --- | --- |
| 인증 | `POST` | `/auth/signup` | 회원가입 및 자동 로그인 |
| 인증 | `POST` | `/auth/login` | 로그인 |
| 인증 | `POST` | `/auth/refresh` | 토큰 재발급 |
| 인증 | `POST` | `/auth/logout` | 로그아웃 |
| 회원 | `GET` | `/members/me` | 본인 정보 조회 |
| 공간 | `GET` | `/spaces` | 공간 목록 및 날짜별 예약 현황 조회 |
| 공간 | `GET` | `/spaces/{spaceId}` | 공간 상세 조회 |
| 공간 | `GET` | `/spaces/{spaceId}/slots` | 예약 가능 시간 조회 |
| 예약 | `POST` | `/reservations` | 예약 생성(슬롯 확보, HOLD) |
| 예약 | `POST` | `/reservations/{reservationId}/pay` | 결제 확인 및 확정(CONFIRMED, Idempotency-Key 필수) |
| 예약 | `GET` | `/reservations` | 본인 예약 목록 조회 |
| 예약 | `GET` | `/reservations/{reservationId}` | 본인 예약 상세 조회 |
| 예약 | `POST` | `/reservations/{reservationId}/cancel` | 본인 예약 취소 |
| 예약 | `POST` | `/reservations/{reservationId}/extend` | 본인 예약 연장 |
| 예약 | `POST` | `/reservations/{reservationId}/check-out` | 체크아웃 |
| 출입 | `POST` | `/reservations/{reservationId}/access-keys` | 출입 키 발급·재발급 |
| 출입 | `POST` | `/access-attempts` | 최초 체크인 및 재입장 검증 |
| 관리자 | `POST` | `/admin/members/{memberId}/credits` | 크레딧 지급 |
| 관리자 | `GET` | `/admin/spaces` | 전체 공간 조회 |
| 관리자 | `POST` | `/admin/spaces` | 공간 등록 |
| 관리자 | `GET` | `/admin/spaces/{spaceId}` | 공간 수정용 상세 조회 |
| 관리자 | `PUT` | `/admin/spaces/{spaceId}` | 공간 정보 전체 수정 |
| 관리자 | `GET` | `/admin/reservations` | 전체 예약 조회 |
| 관리자 | `GET` | `/admin/reservations/{reservationId}` | 예약 및 출입 이력 조회 |
| 관리자 | `POST` | `/admin/reservations/{reservationId}/force-cancel` | 사유를 기록한 예약 강제 취소 |
| 관리자 | `GET` | `/admin/members` | 회원 목록 및 최근 상태 변경 이력 조회 |
| 관리자 | `PATCH` | `/admin/members/{memberId}/status` | 일반 회원 정지·복구 |
- 공간 조회는 비로그인 상태에서도 가능합니다.
- 결제 확정(`/pay`) 요청에는 중복 처리를 방지하는 `Idempotency-Key` 헤더가 필수입니다(예약 생성 자체에는 결제가 없어 불필요).
- 본인 예약 조회·취소·연장·출입 키 발급·체크아웃은 예약자 본인만 가능합니다.
- 관리자 강제 취소는 별도 관리자 API에서 사유와 감사 로그를 기록하여 처리합니다.
- 결제(크레딧 차감)는 예약 생성과 별도 단계(`/pay`)에서 처리하며, 취소 시 환급은 취소와 같은 트랜잭션에서 즉시 처리되어 별도 재처리 작업이 필요 없습니다.

상세 요청·응답과 오류 코드는 Notion API 명세 및 Swagger UI에서 관리할 예정입니다.

## 🧪 테스트 전략

정상 흐름뿐 아니라 동시 요청, 부분 실패, 상태 변경 직후와 시간 경계를 검증합니다.

| 계층 | 검증 대상 |
| --- | --- |
| 단위 테스트 | 요금 계산, 시간 조건, 상태 전이, 인가 규칙 |
| 통합 테스트 | 트랜잭션 롤백, DB 제약 조건, 동시 예약 |
| API 테스트 | 인증·인가, 요청 검증, 응답 및 오류 코드 |
| 배포 환경 검증 | 회원가입부터 예약·출입·완료까지의 전체 흐름 |

시간 기반 로직에는 `Clock`을 주입하여 실제 대기 없이 경계 시각을 검증할 예정입니다.

### 핵심 테스트 시나리오

| 대상 | 검증 내용 |
| --- | --- |
| 동시 예약 | 동일 공간의 겹치는 시간에 여러 요청이 들어와도 하나만 성공하는지 검증 |
| 부분 시간 중복 | 기존 예약과 일부 시간만 겹치는 요청도 거절하는지 검증 |
| 요청 멱등성 | 동일 키와 요청을 재전송해도 예약과 결제가 중복 처리되지 않는지 검증 |
| 결제 실패 | 크레딧 잔액 부족 시 결제만 롤백되고 예약은 `HOLD`로 남아 재시도할 수 있는지 검증 |
| 요금 보존 | 공간 요금 변경 후에도 기존 예약 금액이 유지되는지 검증 |
| 계정 정지 | 정지 직후 기존 Access Token 요청이 거절되는지 검증 |
| 출입 키 무효화 | 예약 취소 및 키 재발급 직후 기존 키가 거절되는지 검증 |
| 시간 경계 | 출입 키 발급·출입·예약 취소의 경계 시각 검증 |
| 소유권 | 일반 회원과 관리자 모두 타인 예약을 취소하거나 키를 발급할 수 없는지 검증 |
| 상태 경합 | 취소·완료 요청이 겹쳐도 상태와 이력이 중복 변경되지 않는지 검증 |
| 연장 동시성 | 연장과 신규 예약이 같은 슬롯을 동시에 요청해도 하나만 성공하는지 검증 |
| 노쇼 | 시작+15분까지 미체크인이면 `NO_SHOW`로 전이하고 슬롯이 반환되는지(환불 없이) 검증 |
| 크레딧 불변식 | 임의 시점에 `SUM(credit_transaction.amount) == member.balance`가 성립하는지 검증 |

테스트 결과는 구현 완료 후 실행 환경과 함께 기록합니다.

## 🚀 프로젝트 구조 및 실행

### 저장소 구조

```
NBE12-14-2-OVENGERS/
├── backend/                # Spring Boot
├── frontend/               # React
├── docs/                   # ERD, API, 설계 및 테스트 문서
├── .github/
│   ├── ISSUE_TEMPLATE/
│   ├── pull_request_template.md
│   └── workflows/
├── .gitignore
└── README.md
```

핵심 설계와 문제 해결 과정은 다음 형식으로 `docs/`에 기록합니다.

```markdown
docs/
├── requirements.md
├── system-architecture.md
├── erd.md
├── api-spec.md
├── test-strategy.md
├── test-results.md
├── troubleshooting.md
└── decisions/
    ├── reservation-concurrency.md
    ├── reservation-idempotency.md
    ├── access-key-policy.md
    └── authorization-policy.md
```

### 로컬 실행

프로젝트 초기 환경 구성이 완료되면 아래 내용을 실제 실행 결과와 함께 작성합니다.

```
# Backend
cd backend
./gradlew bootRun

# Frontend
cd frontend
npm install
npm run dev
```

추가할 실행 정보:

- JDK, Node.js, MySQL 버전
- 필수 환경변수와 예제 파일
- 데이터베이스 초기화 방법
- 개발·시연용 데이터 생성 방법
- 테스트 실행 명령
- Swagger UI 주소

실제 비밀값이 담긴 `.env`는 커밋하지 않습니다. 필요한 환경변수 이름은 예제 파일로 제공합니다.

## 🤝 협업 규칙

### PR 
PR 하나 당 400줄 이내의 변경사항을 가집니다. 

### 브랜치 전략

기능 개발은 `feat/* → dev`, 배포 반영은 `dev → main` PR로 진행합니다.

```
main
├── dev
│   ├── feat/{description}
│   ├── refactor/{description}
│   ├── test/{description}
│   └── docs/{description}
│   ├── perf/{description}
│   ├── chore/{description}
└── hotfix/{description}
```

| 브랜치          | 분기 기준 | 병합 대상 | 용도                |
|--------------| --- | --- |-------------------|
| `main`       | — | — | 배포 가능한 코드         |
| `dev`        | `main` | `main` | 다음 배포를 위한 통합      |
| `feat/*`     | `dev` | `dev` | 새로운 기능 개발         |
| `refactor/*` | `dev` | `dev` | 리팩토링              |
| `test/*`     | `dev` | `dev` | 독립적인 테스트 추가·개선    |
| `docs/*`     | `dev` | `dev` | README 및 개발 문서 변경 |
| `perf/*`     | `dev` | `dev` | 성능 향상             |
| `chore/*`    | `dev` | `dev` | 사소한 작업            |
| `hotfix/*`   | `main` | `main`, `dev` | 배포 환경의 긴급 장애 수정   |

### 커밋 컨벤션

```
type(scope): a sentence describing the change
```

```markdown
스코프는 auth, member, space, reservation, access 등 변경 대상 도메인을 사용합니다.
커밋 메시지 본문은 영어 명령형 현재시제로 작성합니다. (add, fix, block — added/adds 아님)

예시)
feat(reservation): store price snapshot when a reservation is confirmed
fix(access): block access key usage for cancelled reservations
test(auth): verify token requests from suspended accounts
refactor(reservation): extract reservation status transition logic
docs(readme): add local setup instructions
chore(ci): add PR build and test workflow
```

### 데이터베이스 마이그레이션 (Flyway)

DDL은 Flyway로 관리합니다. `backend/src/main/resources/db/migration/`에 `V{버전}__{설명}.sql` 형식(버전과 설명 사이는 더블 언더스코어)으로 파일을 추가합니다.

버전 번호는 담당 엔터티 순서로 고정하며, FK 의존 순서(회원/공간 → 예약 → 결제/출입)와 일치합니다.

| 버전 | 담당자 | 테이블 |
| --- | --- | --- |
| `V1` | 천종원 | `member`, `refresh_token` |
| `V2` | 김재철 | `space`, `audit_log` |
| `V3` | 이태호 | `reservation`, `reservation_slot`, `reservation_status_history` |
| `V4` | — | ~~`payment`~~ (2026-09-15 삭제 확정, `core-domain-decisions.md` §1-1 — 아직 작성되지 않은 채로 삭제됨) |
| `V5` | 박창현 | `door_access_token`, `door_access_log` |
| `V6` | 백한비 (구 `payment` 담당) | `credit_transaction` (신규, §11) |

- 한번 `dev`에 병합된 `V` 파일은 수정하지 않습니다. 변경이 필요하면 새 버전 파일을 추가합니다.
- 로컬 실행은 `application-local.yml`의 MySQL 접속 정보를 사용하고, `spring.jpa.hibernate.ddl-auto`는 `none`으로 고정해 Hibernate가 스키마를 건드리지 않게 합니다.

### 이슈 및 PR

- 이슈에는 작업 배경, 범위, 완료 조건을 작성합니다.
- PR은 하나의 목적에 집중하고 관련 이슈를 연결합니다.
- PR 본문에는 변경 이유, 주요 동작, 테스트 결과를 작성합니다.
- API·스키마·환경변수 변경은 영향 범위와 적용 방법을 함께 설명합니다.
- `main`과 `dev`에 브랜치 보호 규칙을 적용합니다.
- 작성자를 제외한 **팀원 2명 이상의 Approve**를 병합 조건으로 설정합니다.
- 필수 CI 통과와 리뷰 대화 해결 후 병합합니다.
- Merge 방식은 `Squash and merge`를 사용합니다.

### 코드 리뷰 기준

| 우선순위 | 검토 내용 |
| --- | --- |
| 1 | 요구사항과 비즈니스 규칙 충족 여부 |
| 2 | 인증·역할·소유권·상태 검사 누락 여부 |
| 3 | 동시 요청 및 실패 시 데이터 정합성 |
| 4 | 트랜잭션 범위와 상태 전이 |
| 5 | 경계·실패 상황을 검증하는 테스트 |
| 6 | 코드 구조, 이름, 중복과 가독성 |

### 코드 리뷰 코멘트 규칙

| Prefix | Blocking | 의미 |
| --- | --- | --- |
| `issue:` | Yes | 오류·보안·데이터 정합성·명세 위반 |
| `suggestion:` | No | 구조와 유지보수성을 위한 개선 제안 |
| `question:` | 상황에 따라 다름 | 설계 의도나 동작 확인 |
| `nit:` | No | 사소한 표현·스타일 의견 |
| `praise:` | No | 잘 구현된 부분에 대한 피드백 |

병합을 막는 의견에는 `issue (blocking):`을 명시하고, 나머지는 기본적으로 non-blocking으로 취급합니다.

첫 리뷰는 업무일 기준 1일 이내를 목표로 합니다.

## 👥 팀원 및 역할

| 담당 엔터티(CRUD) | 담당자 | GitHub                               |
| --- |-----|--------------------------------------|
| `space`, `audit_log` | 김재철 | [@Lemnoideae](https://github.com/Lemnoideae) |
| `door_access_token`, `door_access_log` | 박창현 | [@pch112233456-a11y](https://github.com/pch112233456-a11y) |
| `credit_transaction`, `reservation_status_history` | 백한비 | [@jkidse14](https://github.com/jkidse14) |
| `reservation`, `reservation_slot` | 이태호 | [@anton061311](https://github.com/anton061311) |
| `member`, `refresh_token` | 천종원 | [@vvipia](https://github.com/vvipia) |

## 🗓️ 개발 일정

| 기간 | 목표 |
| --- | --- |
| 2026.09.14 ~ 09.18 | 초기 배포·CI, 인증, 공간 조회, 예약 생성·취소 |
| 2026.09.21 ~ 09.23 | 출입 키 발급·검증, 예약 완료 처리, 주요 흐름 통합 |
| 2026.09.28 ~ 10.01 | 회귀 테스트, 사용자 피드백, 문서·시연·발표 |

## ✅ Definition of Done

- [ ]  배포 환경에서 회원가입 → 예약 → 출입 → 이용 완료 흐름이 동작한다.
- [ ]  동일 공간의 겹치는 예약 요청 중 하나만 성공한다.
- [ ]  같은 Idempotency-Key로 결제 확정 요청을 재전송해도 크레딧 차감이 중복 처리되지 않는다.
- [ ]  크레딧 잔액 부족 시 결제만 롤백되고 예약은 `HOLD`로 남아 재시도할 수 있다.
- [ ]  임의 시점에 `SUM(credit_transaction.amount) == member.balance`가 성립한다.
- [ ]  시작+15분까지 미체크인이면 `NO_SHOW`로 전이되고 슬롯이 반환된다(환불 없음).
- [ ]  예약 취소·이용 종료·계정 정지가 출입 판단에 반영된다.
- [ ]  공간 요금 변경 후에도 기존 예약 금액이 유지된다.
- [ ]  일반 회원과 관리자 모두 예약 소유권 정책을 준수한다.
- [ ]  실제 MySQL 환경에서 통합·동시성 테스트가 통과한다.
- [ ]  README·API 명세·ERD·실제 구현이 일치한다.
- [ ]  실행 방법, 공개 URL, API 문서와 시연 영상이 제공된다.

## 📝 기술적 의사결정

```
문제 상황
  → 요구 조건
  → 검토한 대안
  → 선택 이유
  → 검증 방법과 결과
  → 남은 한계
```

주요 기록 대상:

- 겹치는 예약을 차단하는 동시성 제어 방식
- 예약(HOLD)과 결제 확정(크레딧 차감)의 트랜잭션 범위, 연장 시 동시성 처리
- 동일 예약 요청의 멱등성 처리
- 예약당 활성 출입 키 제한
- 계정 상태 변경을 즉시 반영하는 인가 구조
- 공간 요금 변경 이후 기존 예약 금액 보존

구현이 완료되면 실제 코드, 테스트 결과와 측정 조건을 근거로 문서를 갱신합니다.