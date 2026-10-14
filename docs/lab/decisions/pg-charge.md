# PG 결제 — 크레딧 충전

> 상태: 구현 중 (4단계 완료, 5단계 진행 전). 작성: 2026-10-06. 결정 반영: 2026-10-14.

## 문제

- 크레딧을 늘리는 경로가 관리자 지급(`CreditGrantService`) 하나로 보인다. 사용자가 직접 돈을 내고 충전하는 경로가 없다. (지급 서비스의 상세는 구현 전에 코드를 읽어 확인한다.)
- 돈이 오가는 흐름은 "승인 후 서버 장애", "같은 요청의 중복", "금액 위변조", "응답 없음(미확정)"을 반드시 다뤄야 한다.

## 범위

- **PG사를 구현하지 않는다.** 토스페이먼츠의 공식 SDK(**결제창형** 일반 결제)와 서버 API를 **테스트 키**로 연동한다. 실제 돈은 움직이지 않는다. (결제위젯(주문서형)은 쓰지 않는다: 서버 로직은 같고 프론트 작업만 커진다.)
- 결제 대상은 **크레딧 충전**이다. 예약 결제는 기존대로 크레딧 차감(`ReservationPaymentConfirmService`)이며 바꾸지 않는다. 예약을 카드로 직접 결제하는 방식은 `core-domain-decisions.md`(payment 테이블 삭제, 크레딧 원장 단일화)와 규칙 2에 충돌하므로 채택하지 않는다.
- 일회성 카드 결제 하나에 집중한다. 정기결제, 정산, 복합 결제수단은 제외한다.
- **충전 취소(환불)는 제외한다.** 시간이 남으면 후속으로 "충전 직후 미사용분 전액 취소"를 검토한다 (예약 결제와 동시에 오면 잔액이 어긋나는 문제가 새로 생긴다).
- 참고 자료: 가이드 "공유오피스 예약 프로젝트 · 결제 백엔드 구현 가이드"의 요구사항 중 충전에 해당하는 것(① 검증 ② 기록·상태 ④ 멱등성 ⑥ 복구·웹훅 ⑦ 보안)을 적용한다. ③ 동시 예약은 2차에서 이미 구현돼 있다.

## 용어 주의

기존 `CreditService.charge(memberId, reservationId, amount)`는 **예약 결제 시 크레딧을 차감**한다. 이름이 겹치므로 충전은 `TOP_UP`으로 부른다.

## 선택안

### 상태

`charge_order` 한 행이 결제 **시도** 하나다. 실패한 시도를 덮어쓰지 않고, 재시도하면 새 주문을 만든다.

| 상태 | 의미 |
| --- | --- |
| `PENDING` | 주문이 생성됨. 결제창 전 |
| `CONFIRMING` | 승인 요청을 시작함. 결과를 아직 확정하지 못함 (타임아웃 포함) |
| `PAID` | 승인이 확인되고 크레딧이 반영됨 |
| `FAILED` | PG가 **명확히 실패**를 알려 줌 |

실패와 미확정은 다르다. 응답을 받지 못한 것은 `FAILED`가 아니라 `CONFIRMING`으로 남고, 복구 대상이 된다. (복구 한도를 넘긴 건을 관리자 확인 대상으로 표시하는 방식은 구현 시 정한다.)

### 흐름

1. `POST /credits/charge-orders` — 서버가 **허용한 충전 금액**(최소·최대·100원 단위 규칙)을 검증하고, `orderId`와 금액을 만들어 `charge_order`(`PENDING`)에 저장한다.
2. 프론트가 클라이언트 키로 SDK의 **결제 요청 메서드**를 호출해 결제창을 연다(`orderId`, `successUrl`, `failUrl` 전달). 인증이 끝나면 성공 URL로 리다이렉트되고 쿼리에 `paymentKey`, `orderId`, `amount`(와 `status`)가 붙는다. **프론트는 승인하지 않고 우리 서버의 승인 API만 호출한다.**
3. `POST /credits/charge-orders/confirm` (`Idempotency-Key` 필수) — 짧은 DB → PG 통신 → 짧은 DB의 3단계다.
   - **DB 1 (짧은 트랜잭션):** 본인 주문인지와 회원이 `ACTIVE`인지 먼저 확인한다. 두 검사를 통과한 요청의 금액이 저장 금액과 다르면 감사 로그를 남기고, 이 기록은 이후 멱등키 재사용 거절과 겹쳐도 유지한다. 그 뒤 `PENDING` 상태와 클라이언트 `Idempotency-Key` 재사용 여부를 확인한다. 같은 회원의 다른 주문에 이미 사용된 키면 409 `IDEMPOTENCY_KEY_REUSED`로 거절한다. 검증을 통과하면 `PENDING → CONFIRMING`을 **조건부 UPDATE**로 바꾸고(동시에 와도 한 요청만 통과), 클라이언트 멱등키와 PG에 보낼 **멱등키**를 PG 호출 전에 저장한다.
   - **PG 승인 호출 (트랜잭션 밖):** 재시도할 때는 같은 멱등키를 쓴다.
   - **DB 2 (짧은 트랜잭션):** 승인이 확인되면 한 트랜잭션에서 `CONFIRMING → PAID`, `credit_transaction`에 `TOP_UP` 행, 잔액 증가.
   - PG가 명확히 거절하면 `FAILED`. 타임아웃·5xx면 `CONFIRMING`을 유지하고 **새 결제를 만들지 않는다**.
4. **복구 스케줄러:** 오래 `CONFIRMING`인 주문을 PG에 조회해 승인됐으면 DB 2와 같은 경로로 반영하고, 실패·만료면 `FAILED`로 바꾼다. 여전히 모르면 간격을 두고 다시 확인한다. (`ReservationCompletionScheduler`의 `processEach` 건별 트랜잭션 패턴 재사용)
5. **웹훅은 보조 수단이다.** 이벤트는 `PAYMENT_STATUS_CHANGED`다. 로컬에서 수신할 수 있음을 확인했다(2026-10-06). 본문의 성공 값을 믿지 않고 PG에 조회한 결과로만 DB 2 경로를 호출한다. 이미 처리된 주문이면 무시한다(중복 수신). **10초 안에 200을 응답해야 하므로**, 수신 즉시 기록하고 200을 주고 처리는 따로 한다. 실패 시 PG가 최대 7회 재전송하며(약 3일 19시간) 같은 알림이 중복·지연될 수 있다. 사용자가 결제창을 닫는 것만으로는 웹훅이 오지 않으므로 웹훅에 의존하지 않고 4번이 기본이다.

### 승인 API 응답 정책 (2026-10-13 확정, 2026-10-14 보완)

| 상황 | 주문 상태 | HTTP 응답 |
| --- | --- | --- |
| 요청 금액이 저장 금액과 다름 | 소유권과 회원 `ACTIVE` 검사를 통과한 요청만 기존 `audit_log` 방식으로 위변조 의심 기록을 남김. 이후 멱등키 재사용 거절과 겹쳐도 기록하며, 단독이면 `PENDING` 유지·PG 미호출 | 400 `CHARGE_ORDER_AMOUNT_MISMATCH` 또는 함께 발생한 멱등키 거절 코드 |
| 승인 요청 시 회원이 `ACTIVE`가 아님 | `PENDING` 유지. DB 1에서 거절하고 PG는 호출하지 않음 | 403 `ACCOUNT_INACTIVE` |
| 같은 회원이 `Idempotency-Key`를 다른 주문에 재사용 | 대상 주문은 `PENDING` 유지. DB 1에서 거절하고 PG는 호출하지 않음 | 409 `IDEMPOTENCY_KEY_REUSED` |
| 이미 `PAID`이고 요청 `paymentKey`가 저장 값과 같음 | `PAID` 유지 | 200 `PAID` (성공 페이지 새로고침 허용) |
| 이미 `CONFIRMING` | `CONFIRMING` 유지. PG 재호출 없음 | 202 `CONFIRMING` |
| 그 밖의 `PENDING` 아닌 상태 | 상태 유지 | 409 `CHARGE_ORDER_STATE_CONFLICT` |
| PG 사용자 측 거절(카드 거절·한도 초과 등) | `FAILED` | 422 `PAYMENT_DECLINED` |
| PG `ALREADY_PROCESSED_PAYMENT` | 실패로 단정하지 않고 PG 결제를 조회. 조회 결과가 승인 상태면 DB 2 경로로 `PAID` 반영 | 조회 결과에 따라 200 `PAID` 또는 202 `CONFIRMING` |
| PG 인증 키·요청 설정 등 우리 측 문제 | `CONFIRMING` 유지 | 500 |
| PG 타임아웃·5xx 등 결과 모름 | `CONFIRMING` 유지 | 202 `CONFIRMING` |
| PG 승인은 확인됐지만 DB 2가 실패 | `CONFIRMING` 유지. `FAILED`로 바꾸지 않고 실패 원인을 로그로 남긴 뒤 복구 스케줄러가 정리 | 202 `CONFIRMING` |
| 결과 모름 뒤 같은 `Idempotency-Key` 재요청 | 저장된 첫 HTTP 응답을 재생하지 않는다. PG를 다시 호출하지 않고 현재 주문 상태를 조회해 응답 | 현재 상태가 `CONFIRMING`이면 202, 이후 복구되어 `PAID`면 200 |
| 주문 없음 | 해당 없음 | 404 `CHARGE_ORDER_NOT_FOUND` |
| 본인 주문 아님 | 상태 유지 | 403 `FORBIDDEN_NOT_OWNER` |

클라이언트 `Idempotency-Key`는 충전 시도 1회용이다. `FAILED` 전이에서도 `client_idempotency_key`를 지우지 않으며, 사용자는 새 충전 주문과 새 멱등키로 다시 시도한다. 실패한 시도의 키까지 계속 점유해야 지연·중복 요청이 다른 결제로 해석되는 일을 막을 수 있다.

PG 오류 분류는 토스페이먼츠 공식 [API 에러 코드](https://docs.tosspayments.com/reference/error-codes)와 [테스트 환경에서 에러 재현하기](https://docs.tosspayments.com/guides/v2/get-started/environment#테스트-환경에서-에러-재현하기)를 구현 시점에 다시 확인한다. 문서에 없는 코드나 분류가 불명확한 응답은 명확한 거절로 간주하지 않고 `CONFIRMING`을 유지한다.

## 승인 결과 분류 (공식 문서 확인, 2026-10-06)

원칙: **`FAILED`로 바꾸는 것은 "명확한 거절"로 확인된 코드뿐이다.** 그 밖의 모든 응답(목록에 없는 새 코드 포함)은 `CONFIRMING`을 유지하고 조회로 확정한다. 응답을 못 받은 것과 거절은 다르기 때문이다.

| 승인 응답 | 처리 |
| --- | --- |
| 200, 결제 상태 `DONE` | DB 2 경로로 `PAID` |
| 400 `ALREADY_PROCESSED_PAYMENT` | 실패가 아니다. 조회해서 `DONE`이면 `PAID`(이전 요청이 이미 성공한 경우) |
| 400 `ALREADY_PROCESSING_REQUEST` | `CONFIRMING` 유지, PG 재호출 없이 조회로 확정 |
| 500 계열(`FAILED_PAYMENT_INTERNAL_SYSTEM_PROCESSING`, `FAILED_INTERNAL_SYSTEM_PROCESSING`, `COMMON_ERROR`, `UNKNOWN_ERROR`), 타임아웃, 네트워크 오류 | `CONFIRMING` 유지, 조회로 확정 (메시지가 "결제 실패"여도 500이면 단정하지 않는다) |
| 명확한 사용자 측 거절: `REJECT_CARD_PAYMENT`, `INVALID_REJECT_CARD`, `REJECT_CARD_COMPANY`, `REJECT_ACCOUNT_PAYMENT`, `INVALID_STOPPED_CARD`, `INVALID_CARD_NUMBER`, `INVALID_CARD_EXPIRATION`, `INVALID_CARD_LOST_OR_STOLEN`, `INVALID_PASSWORD`, `EXCEED_MAX_AUTH_COUNT`, `EXCEED_MAX_AMOUNT`, `EXCEED_MAX_DAILY_PAYMENT_COUNT`, `EXCEED_MAX_PAYMENT_AMOUNT`, `BELOW_MINIMUM_AMOUNT`, `NOT_FOUND_PAYMENT_SESSION`, `PAY_PROCESS_ABORTED`, `PAY_PROCESS_CANCELED` | `FAILED` (사용자가 다시 결제하려면 새 주문) |
| 우리 쪽 설정·요청 오류: `INVALID_API_KEY`, `UNAUTHORIZED_KEY`, `INVALID_CLIENT_KEY`, `API_KEY_ACCESS_DENIED`, `FORBIDDEN_REQUEST`, `INVALID_REQUEST`, `INVALID_PAYMENT_KEY` | `CONFIRMING` 유지, 500을 반환하고 운영 오류로 확인 |
| 위 목록에 없는 코드 | 명확한 거절로 추측하지 않고 `CONFIRMING` 유지 |

조회 결과의 결제 상태 매핑:

| 조회한 결제 상태 | `PgPaymentLookupResult` | 처리 |
| --- | --- | --- |
| `DONE` | `APPROVED` | `PAID` 반영 후보 |
| `ABORTED`(승인 실패), `EXPIRED`(30분 경과) | `FAILED` | 돈이 나가지 않은 것이 확실하므로 `FAILED` 반영 후보 |
| `READY`, `IN_PROGRESS` | `UNKNOWN` | `CONFIRMING` 유지, 간격을 두고 재확인 |
| `CANCELED` | `FAILED` | 취소 완료로 돈이 나가지 않은 것이 확실하므로 경고 로그를 남기고 `FAILED` 반영 후보 |
| `PARTIAL_CANCELED`, `WAITING_FOR_DEPOSIT` | `UNKNOWN` | 이번 범위에서 생기지 않아야 하므로 경고 로그를 남기고 사람이 확인 |

조회 상태 매핑 원칙은 **돈이 확실히 나가지 않은 경우만 `FAILED`로 분류하고, 애매하면 `UNKNOWN`으로 두고 사람이 확인하는 것**이다.

2단계에서 `ALREADY_PROCESSED_PAYMENT` 직후 조회 결과가 `FAILED` 또는 `UNKNOWN`이면 로컬 주문을 `FAILED`로 확정하지 않고 `CONFIRMING`으로 유지한다. PG 조회 상태 전체 매핑과 최종 상태 정리는 4단계 복구 스케줄러에서 처리한다.

구현 규칙:

- 연결 타임아웃은 **3초**, 승인 API 읽기 타임아웃은 **60초**로 둔다(읽기 시간은 공식 권장). 복구 스케줄러가 `CONFIRMING`을 집어 가는 기준은 이보다 충분히 길게 둬서, 아직 승인 호출 중인 건을 건드리지 않는다.
- 승인 호출은 `Idempotency-Key` 헤더를 쓴다. 같은 키로 보내면 첫 응답이 반환되고, **본문이 다르면 오류**다. 키는 최대 300자, 유효 기간 15일이다. 주문당 하나를 만들어 요청 전에 저장하고 재시도에도 그대로 쓴다.
- 조회는 `paymentKey`로도 `orderId`로도 할 수 있다. 응답을 못 받아 `paymentKey`를 모르는 경우에는 `orderId`로 조회한다.
- 응답 JSON 파싱은 **모르는 필드를 무시**한다(공식 문서: 필드 추가는 버전 없이 반영될 수 있다). Jackson이면 `FAIL_ON_UNKNOWN_PROPERTIES=false`.
- 인증은 시크릿 키 뒤에 `:`를 붙여 base64로 인코딩한 Basic 헤더다.
- 카드 결제 최소 금액은 100원이다. 우리 충전 최소 금액은 그보다 크게 정한다.
- `paymentKey`는 승인 응답을 받은 뒤 DB에 반드시 저장한다(조회·취소에 필요). 시크릿 키와 달리 `paymentKey`가 노출돼도 키 없이는 API를 호출할 수 없다.

## 대안과 기각 이유

| 대안 | 기각 이유 |
| --- | --- |
| 프론트가 보낸 금액을 그대로 신뢰 | 금액 위변조가 가능하다 |
| 프론트가 PG 승인을 직접 호출 | 시크릿 키가 노출되고 서버 검증이 사라진다 |
| 웹훅만으로 충전 확정 | 지연, 유실, 중복에 취약하다 |
| 승인 응답만 믿고 보정 로직 없음 | 승인 후 서버가 죽으면 돈만 나가고 크레딧이 없는 상태가 남는다 |
| `@Transactional` 하나로 PG 호출까지 묶기 | 로컬 DB 롤백은 이미 승인된 PG 결제를 되돌리지 못한다. PG 호출 동안 DB 잠금을 오래 잡는 문제도 있다 |
| 응답이 없으면 새 주문으로 다시 결제 | 이중 결제 위험. 먼저 조회로 결과를 확인한다 |
| 예약을 카드로 직접 결제 | 크레딧 원장 단일화 결정과 규칙 2에 충돌한다 |

## 지켜야 할 불변 조건

- 같은 `paymentKey`로 `credit_transaction` 행은 한 번만 생긴다 (DB 유니크 제약으로 강제).
- `charge_order.order_id`는 UNIQUE이고 서버가 만든다.
- 충전 후에도 잔액과 원장 합계가 어긋나지 않는다 (README DoD의 미검증 항목과 연결).
- 시크릿 키는 환경변수로만 두고 프론트, 저장소, 로그에 노출하지 않는다.
- 카드번호·CVC를 직접 받거나 저장하지 않는다 (PG 결제 화면 사용).
- 로그에는 주문번호, 상태, 오류 이유만 남기고 키와 민감정보는 제외한다.

## 결정 (2026-10-06)

- **환불(충전 취소)은 이번 범위에서 제외한다.** 후속 후보: 충전 직후 미사용분 전액 취소.
- **쓰는 키는 `test_gck_`(클라이언트)와 `test_gsk_`(시크릿)다.** 결제창형(주문서형 포함) 연동 키이며, API 개별 연동 키(`ck`/`sk`)는 결제창 구버전·브랜드페이·자동결제용이라 쓰지 않는다. 클라이언트 키와 시크릿 키는 한 세트로 쓰고, 테스트와 라이브를 섞지 않는다(섞으면 `INVALID_API_KEY`). 전자결제 신청 전에는 문서에 공개된 테스트 키로 연동하고, 내 상점 키는 신청 이후에만 확인할 수 있다. 시크릿 키 API 버전은 `2022-11-16`으로 고정이다. 키 값은 이 저장소에 적지 않는다.
- **`orderId`는 `chg_` + 소문자 UUID v4로 생성한다.** 주문서형 SDK와 Core API의 허용 형식 중 더 엄격한 Core API 기준(영문 대소문자·숫자·`-`, `_`, 6~64자)에 맞춘다.
- **샌드박스 키는 환경변수와 로컬 `.env`로만 주입한다.** `.env.example`에는 선택 키를 빈 값으로 활성화하지 않고 **주석 처리된 키 이름만 커밋**하며 `.env`는 커밋하지 않는다.
- **CI는 `FakePgClient`만 사용한다.** CI에 PG 키를 넣지 않는다. 실제 샌드박스 호출은 로컬 수동 검증으로만 한다.
- **PG 호출은 `PgClient` 인터페이스 뒤에 둔다.** HTTP는 Spring의 `RestClient`로 하고 연결·읽기 타임아웃을 설정한다.
- **허용 충전 금액은 최소·최대 + 100원 단위 규칙으로 한다** (2026-10-07, 단위 목록 대신 선택). 기본값: 최소 1,000원, 최대 100,000원, 100원 단위(예약 요금 단위와 같다). 금액은 원 단위 정수(`long`)로만 다룬다. 경계 테스트: 0, 음수, 999, 1,000, 1,100(허용), 1,001(거절), 100,000, 100,100(거절).
- **프론트:** 결제창형이라 위젯 렌더링은 없다. 충전 화면 1개(금액 선택, 결제 버튼)와 성공·실패 URL 처리 페이지가 필요하다.

## 검증 계획

자동화 테스트는 두 종류로 나눈다. 실제 샌드박스는 정상 충전 1회를 수동으로 확인하는 용도이고, 장애·동시성은 PG 응답을 흉내 내는 도구(WireMock)와 기존 Testcontainers MySQL로 자동화한다. PG 테스트 서버에 대량 요청을 보내지 않는다.

| 시나리오 | 기대 결과 |
| --- | --- |
| 정상 충전 | 주문 `PAID`, 원장 `TOP_UP` 1행, 잔액 증가 |
| 요청 금액 변조 | 거절 |
| 남의 주문 승인 시도 | 차단 |
| 같은 `Idempotency-Key`로 승인 반복 | 충전 1회 |
| 같은 주문 승인 동시 요청 | 충전 1회 (조건부 UPDATE로 한 요청만 통과) |
| 승인 요청과 웹훅이 동시에 도착 | 충전 1회 |
| 웹훅 중복 수신 | 충전 1회 |
| PG 응답 없음(타임아웃) | `FAILED`로 단정하지 않고 새 결제를 만들지 않으며, 조회 복구로 이어짐 |
| PG 승인 후 DB 저장 실패 | 남겨 둔 `CONFIRMING` 기록으로 복구됨 |
| 복구 중에도 결과를 알 수 없음 | `CONFIRMING` 유지, 재확인, 한도 초과 시 표시 |
| 충전 후 잔액·원장 | 합계 일치 |

- PG 호출은 `PgClient` 인터페이스 뒤에 두고, 단위 테스트에서는 `FakePgClient`를, HTTP 수준 장애 재현에는 WireMock을 쓴다.
- 승인 처리 중에 응답이 오지 않는 경우를 최소 하나 이상 직접 재현하고 복구 전후 기록을 남긴다.

## 열린 질문

- 웹훅 `PAYMENT_STATUS_CHANGED`의 본문 구조와, 서명 등 검증용 값이 이벤트에 들어 있는지 (웹훅 가이드에는 검증 방법이 없었다).
- 인증 후 승인까지의 제한 시간(문서에서 숫자를 찾지 못함. `NOT_FOUND_PAYMENT_SESSION` 에러와 `EXPIRED` 30분이 관련됨).
- 복구 한도를 넘긴 `CONFIRMING` 건을 관리자에게 어떻게 보여 줄지.

## 검증 결과

### 2단계 — 승인 API (2026-10-14)

- `POST /api/v1/credits/charge-orders/confirm`과 `PgClient` 경계를 추가했다. DB 1과 DB 2는 각각 별도 `@Transactional` 서비스 메서드이며, 둘을 호출하는 승인 오케스트레이터에는 트랜잭션을 두지 않았다.
- 테스트 전용 `FakePgClient`가 승인·명확한 거절·결과 모름과 호출 지연을 선택할 수 있다. Fake 내부에서 `TransactionSynchronizationManager.isActualTransactionActive()`를 기록하는 MySQL 통합 테스트로 승인 및 조회 호출 모두 트랜잭션 밖(`false`)임을 확인했다.
- 같은 주문의 동시 승인 테스트는 실제 MySQL에서 고정 스레드 2개를 만들고, 두 스레드가 `ready` 래치에 도착한 뒤 `start` 래치를 동시에 해제한다. Fake PG를 400ms 지연시켜 두 번째 요청이 첫 승인 호출 중 DB 1을 통과하도록 했고, PG 호출 1회·`PAID` 응답 1건·`CONFIRMING` 응답 1건·TOP_UP 1건을 확인했다.
- 금액 불일치는 `PENDING`을 유지하고 `audit_logs`에 `CHARGE_AMOUNT_MISMATCH`/`CHARGE_ORDER` 기록을 커밋한 다음 400을 반환한다.
- 공식 문서 출처: 토스페이먼츠 [API 에러 코드](https://docs.tosspayments.com/reference/error-codes), [테스트 환경에서 에러 재현하기](https://docs.tosspayments.com/guides/v2/get-started/environment#테스트-환경에서-에러-재현하기), [LLM Quick Reference](https://docs.tosspayments.com/guides/v2/get-started/llms-quick-reference). 승인 요청은 `POST /v1/payments/confirm`, 본문은 `paymentKey`·`orderId`·`amount`, PG 멱등키는 `Idempotency-Key` 헤더라는 계약을 기준으로 했다.
- 전체 테스트: `./gradlew test --rerun-tasks` 실행 결과 522개 통과, 실패·오류·건너뜀 0개.

### 3단계 — 실제 토스 구현체 (2026-10-14)

- Spring `RestClient` 기반 실제 `PgClient`를 추가했다. 승인 `POST /v1/payments/confirm`, 조회 `GET /v1/payments/{paymentKey}`, Basic 인증과 PG `Idempotency-Key`를 공식 문서 계약대로 구현했다.
- 연결 타임아웃 3초·읽기 타임아웃 60초를 설정했다. 5xx와 연결/읽기 타임아웃·소켓 종료는 `UNKNOWN/INDETERMINATE`로 분류한다.
- test 프로필은 실제 구현체를 제외하고 test source의 `FakePgClient` 하나만 로딩한다. 실제 빈 추가와 함께 승인 서비스는 `ObjectProvider` 대신 일반 생성자 주입을 사용한다.
- 3단계 신규·회귀 테스트 32개로 헤더·본문, 응답/에러 파싱, 모르는 필드, 네트워크 실패, 로그 민감정보 부재, 조회 상태 8개와 상태 누락, 프로필 격리, 타임아웃, 시크릿 미설정 차단, 속성 문자열 마스킹, 승인 응답 주문·금액 대조를 검증했다.
- 최초 PG/credit 범위 테스트는 131개 통과, 실패·오류·건너뜀 0개다. 첫 전체 실행에서 PG 통합 테스트가 남긴 `charge_order`와 `idempotency_key` FK가 뒤 테스트를 막는 문제를 확인해 세 PG 테이블을 자식부터 정리하도록 수정했다. 리뷰 수정 후 최종 전체 테스트는 554개 통과, 실패·오류·건너뜀 0개, 78개 클래스다.
- 공식 문서 공개 테스트 인증으로 실제 호출했다. 인증은 수락됐고 없는 결제 조회는 `404 NOT_FOUND_PAYMENT`, 없는 paymentKey 승인은 `404 NOT_FOUND_PAYMENT_SESSION`의 `{code,message}` 형식이었다. 공개 예시 paymentKey는 현재 200 조회되지 않아 성공 Payment 실응답은 사용자 수동 성공 흐름에서 확인한다.
- 로컬 수동 검증 도구는 앱이 서빙하지 않는 `tools/pg-sandbox/`에 두었다. 키 값은 저장하지 않고 실행 시 입력/환경변수로만 받는다.
- 사용자 수동 검증에서 공개 문서용 테스트 키와 결제창형 샌드박스로 테스트 결제를 만들었다. 서버 승인은 `200 PAID`, 잔액은 50,000원에서 51,000원으로 증가했고, `credit_transaction TOP_UP 1,000원` 1행과 `charge_order PAID`를 확인했다.
- 같은 `paymentKey`를 새 클라이언트 `Idempotency-Key`로 다시 요청해도 `200 PAID`를 반환하고 잔액과 원장이 추가로 바뀌지 않았다. 첫 시도는 만료된 Access Token 때문에 `INVALID_ACCESS_TOKEN`이었고, 재로그인 후 토스 결제 인증의 약 10분 승인 유효시간 안에 성공했다.
- 공식 문서 출처: 토스페이먼츠 [코어 API](https://docs.tosspayments.com/reference), [결제창형 결제 연동](https://docs.tosspayments.com/guides/v2/payment-widget/integration-window), [인증 및 기타 헤더](https://docs.tosspayments.com/reference/using-api/authorization), [API 에러 코드](https://docs.tosspayments.com/reference/error-codes), [환경 설정](https://docs.tosspayments.com/guides/v2/get-started/environment), [API 키](https://docs.tosspayments.com/reference/using-api/api-keys).

### 4단계 — 복구 스케줄러 (2026-10-14)

- `CONFIRMING` 전이 1분 뒤부터 24시간 전까지 저장된 `paymentKey`로 조회하며, `recovery_next_attempt_at`을 별도로 이동해 50건 제한에서도 뒤 주문이 굶지 않게 했다. 정확히 24시간이 된 주문과 복구 불변식·응답 무결성 위반은 `recovery_exhausted_at`으로 한 번만 수동 처리 표시한다.
- 조회 응답의 `paymentKey`, `orderId`, `totalAmount`, 원시 상태, `currency=KRW`를 보존·대조한다. Payment 객체 자체를 받지 못한 전송 실패·타임아웃·5xx·404는 사실 없는 `UNKNOWN`으로 재시도하고, Payment 객체를 받았지만 필수 필드가 없거나 값이 다르면 즉시 수동 처리 대상으로 표시한다. 승인 결과는 저장된 클라이언트 멱등키로 기존 DB 2를 재사용하고, 실패 결과는 `PG_STATUS_<원시 상태>`로 기록한다. MID와 오래된 `PENDING` 정리는 백로그로 남겼다.
- PG 조회는 트랜잭션 밖에서 실행하며 주문별 짧은 트랜잭션과 상태 검사, 주문당 `TOP_UP` UNIQUE로 로컬 부작용을 한 번만 반영한다. `recovery_exhausted_at`을 기록할 때 시스템 작업 감사 로그를 같은 트랜잭션에 한 번만 남긴다. 승인과 로컬 `FAILED` 전이가 경합하면 재개방하거나 예외를 전파하지 않고 실제 `PG_STATUS`·`LOCAL_STATUS`를 감사 로그에 남긴다.
- 로컬 MySQL 8.4의 10,000행 임시 테이블에서 실제 후보 조건으로 `EXPLAIN`했다. 도래 후보는 `idx_charge_order_recovery_due`, 24시간 초과 후보는 `idx_charge_order_recovery_exhausted`를 각각 `range`로 사용했다. 이 검사는 자동 테스트에 넣지 않았다.
- 검증 리뷰 P1 반영 뒤 최종 전체 테스트는 `./gradlew test --rerun-tasks`로 80개 테스트 스위트, 582개 테스트가 통과했고 실패·오류·건너뜀은 0개였다. 상세 구현·검증 기록은 `docs/lab/notes/pg-step-4.md`에 둔다.

## 구현 단계

### 공통 규칙

- `AGENTS.md`와 기존 코드 컨벤션(패키지 구조, 언어, 테스트 스타일)을 먼저 확인하고 따른다.
- 토스 API, 필드명, 헤더, 에러 코드, 결제 SDK 메서드는 기억으로 쓰지 않고 `tosspayments-integration-guide` MCP로 확인한 뒤 사용한다. 확인한 공식 문서 출처를 단계 보고에 적는다.
- 설계 문서와 공식 문서가 다르거나 설계에 빠진 결정이 있으면 추측해서 구현하지 않고 멈춰서 사용자에게 묻는다.
- 각 단계는 테스트를 먼저 작성하고 실패를 확인한 뒤 구현한다.
- 단계와 관련 없는 코드는 건드리지 않고 리팩터링하지 않는다.
- 키 값은 코드와 커밋에 넣지 않는다. `.env`와 환경변수로만 받고 `.env.example`에는 선택 키를 주석 처리된 키 이름으로만 둔다.
- CI 테스트는 `FakePgClient`만 사용하며 실제 토스 호출을 넣지 않는다.

### 진행 및 리뷰 기준

- 한 번에 한 단계만 구현하고, 사용자가 "다음"이라고 하기 전에는 다음 단계로 넘어가지 않는다.
- 단계가 끝나면 커밋하지 않고 변경 파일, 추가 테스트와 검증 내용, 테스트 실행 결과, 리뷰할 설계 판단 2~3개를 보고한다. 단계 보고 전문은 `docs/lab/notes/pg-step-{번호}.md`에 저장한다.
- 구현자가 수정 사항을 반영한 뒤 검증자가 재리뷰 1회를 수행한다.
- P2 항목은 구현 범위에 섞지 않고 `docs/lab/notes/pg-backlog.md`에 항목별로 기록한다.
- 단, PG 승인은 됐는데 크레딧이 반영되지 않거나 주문이 복구 불가 상태로 남을 수 있는 문제, 또는 감사 로그 같은 보안 기록이 사라질 수 있는 문제는 P2라도 현재 단계에서 처리한다.
- 커밋 전에 커밋별 파일 목록을 사용자에게 보여 주고 확인을 받는다.

### 1단계 — 충전 주문 생성

**상태: 완료**

- `POST /api/v1/credits/charge-orders`로 `charge_order(PENDING)`를 저장한다.
- 금액 경계값 `0`, 음수, `999`, `1,000`, `1,001`, `1,100`, `100,000`, `100,100`을 테스트한다.
- `orderId`는 `chg_` + 소문자 UUID v4이며, 숫자 PK와 별도 컬럼에 두고 UNIQUE 제약을 건다.

### 2단계 — 승인 API와 PG 추상화

**상태: 완료**

- `PgClient` 인터페이스와 `FakePgClient`를 추가한다.
- `POST /api/v1/credits/charge-orders/confirm`은 `Idempotency-Key` 헤더를 필수로 받고, 본문으로 `paymentKey`, `orderId`, `amount`를 받는다.
- DB 1 → 트랜잭션 밖 PG 승인 호출 → DB 2의 3단계 구조로 처리한다.
- 다음을 테스트한다.
  - 본인 주문이 아니면 거절하고 상태를 바꾸지 않는다.
  - 요청 금액이 저장 금액과 다르면 거절하고 PG를 호출하지 않는다.
  - `PENDING`이 아닌 주문을 응답 정책에 따라 처리한다.
  - 같은 주문의 동시 승인 요청 2건 중 1건만 PG 호출과 승인을 통과한다.
  - 같은 `Idempotency-Key` 재요청으로 크레딧이 중복 증가하지 않는다.
  - PG의 명확한 거절은 `FAILED`로 바꾸고 크레딧을 증가시키지 않는다.
  - PG 타임아웃·5xx 등 결과 모름은 `CONFIRMING`을 유지하고 크레딧을 증가시키거나 새 결제 시도를 만들지 않는다.
  - 승인 성공은 `PAID` 전이, `TOP_UP` 기록, 잔액 증가를 한 트랜잭션에서 처리한다.
  - 모든 경우에 `SUM(credit_transaction.amount) == member.balance`를 유지한다.
  - Fake 내부에서 `TransactionSynchronizationManager.isActualTransactionActive()`가 `false`인지 확인해 PG 호출이 트랜잭션 밖임을 증명한다.
- 동시성 검증은 기존 Testcontainers MySQL 방식으로 수행한다.

### 3단계 — 실제 토스 구현체

**상태: 완료**

- Spring `RestClient` 기반 실제 토스 구현체를 추가하고 연결·읽기 타임아웃과 PG `Idempotency-Key` 헤더를 설정한다.
- 자동 테스트에서 실제 토스를 호출하지 않는다. 로컬 샌드박스 수동 검증 방법을 `.http` 파일로 남긴다.

### 4단계 — 복구 스케줄러

**상태: 완료 (2026-10-14)**

- 대상은 `CONFIRMING` 전이 시각(`updated_at`)부터 **1분 이상 24시간 미만** 지난 주문이다. 정확히 24시간이 된 주문부터 자동 조회를 중단한다. 주문 생성 시각은 결제창 체류 시간을 포함하므로 기준으로 쓰지 않는다.
- 1분 `fixedDelay`로 실행하고 최초 실행도 1분 뒤로 미룬다. 한 번에 PG 조회 후보 최대 50건, 24시간 초과 표시 대상 최대 50건을 각각 처리한다. 테스트 프로필에서는 자동 실행을 끄고 스케줄러 메서드를 직접 호출한다.
- `recovery_next_attempt_at`으로 다음 조회 시각을 관리한다. `PENDING → CONFIRMING` 때 `now + 1분`으로 설정하고, 조회 결과가 `UNKNOWN`이거나 예외가 발생하면 다시 `now + 1분`으로 미룬다. 후보는 이 시각과 id 순서로 골라 장시간 `UNKNOWN`인 주문이 뒤 주문을 굶기지 않게 한다. 재시도 시 `updated_at`은 바꾸지 않아 24시간 한도의 기준을 보존한다.
- 저장된 `paymentKey`로 PG 결제 조회 API를 호출한다. PG 응답의 `paymentKey`, `orderId`, `totalAmount`, `status`, `currency=KRW`를 로컬 주문과 대조한 뒤에만 결과를 반영한다. MID 대조는 후속 백로그로 둔다.
- 조회 결과가 `APPROVED`면 주문에 저장된 최초 `client_idempotency_key`를 사용해 2단계 DB 2와 같은 경로로 `PAID` 전이·`TOP_UP` 원장·잔액 증가·성공 멱등 응답 저장을 한 트랜잭션에서 처리한다. 회원이 승인 요청 뒤 정지·탈퇴했더라도 이미 PG 승인이 확인됐으면 회원 행이 존재하는 한 적립한다.
- 조회 결과가 `FAILED`면 최종 트랜잭션 시점에도 `CONFIRMING`인 주문만 `FAILED`로 바꾸고 `failure_code`에는 `PG_STATUS_<원시 상태>`를 저장한다. `UNKNOWN`과 조회/DB 예외는 `CONFIRMING`을 유지해 다음 주기에 다시 확인한다.
- **결정 8 명확화:** 복구 필수값(`payment_key`, `client_idempotency_key`, CONFIRMING 시작 시각)이 없으면 자동 보정하거나 `FAILED`로 단정하지 않고 즉시 수동 처리 대상으로 표시한다. PG에서 Payment 객체 자체를 받지 못한 전송 실패·타임아웃·5xx·404는 대조할 사실이 없는 `UNKNOWN`이므로 `CONFIRMING`을 유지해 24시간까지 재시도한다. 반대로 Payment 객체를 받았는데 `paymentKey`·`orderId`·`totalAmount`·원시 상태·`currency` 중 필수값이 없거나 로컬 주문과 다르면 무결성 위반으로 즉시 수동 처리 대상으로 표시한다. 이번 단계에는 orderId 조회 fallback과 새 멱등키 생성을 넣지 않는다.
- 24시간이 된 주문은 `CONFIRMING`을 유지하고 `recovery_exhausted_at`을 조건부로 한 번만 기록한 뒤 경고 로그를 남긴다. 이후 PG 조회 대상에서 제외하며 같은 주문의 경고를 매분 반복하지 않는다.
- 후보 조회와 PG 호출에는 트랜잭션을 두지 않고, 최종 반영은 주문 1건당 별도 트랜잭션으로 처리한다. 겹친 실행의 중복 PG GET은 허용하되, 주문 잠금·상태 검사·`credit_transaction.charge_order_id` UNIQUE로 로컬 부작용을 정확히 한 번만 보장한다. PG 통신 중 DB 락은 유지하지 않는다.
- 최종 반영 시 이미 `PAID`면 성공으로 건너뛰고, 이미 `FAILED`면 자동 재개방하거나 덮어쓰지 않는다. PG 승인이 확인됐는데 로컬이 `FAILED`인 경합은 해당 건에서 잡아 다음 건 처리를 계속하고, 에러 코드가 아니라 실제 PG 상태와 로컬 상태를 시스템 감사 로그에 남긴다. FAILED 조회도 이미 `PAID`인 주문을 덮어쓰지 않는다.
- 승인 후 DB 2 실패 경합의 HTTP 응답은 `PAID → 200`, `CONFIRMING → 202`, 그 밖의 상태 충돌 → 409로 고정해 `200 + FAILED` 응답을 허용하지 않는다.
- 후보 조회용 복합 인덱스를 추가한다. 인덱스 사용 여부는 자동 테스트로 고정하지 않고 로컬 MySQL에서 `EXPLAIN`을 한 번 실행해 단계 보고서에 결과를 기록한다.
- 기존 `ReservationCompletionScheduler`처럼 후보 조정과 건별 처리 빈을 분리하고, 한 건의 예외가 다음 주문 처리를 막지 않게 한다.
- 자동 테스트는 Fake PG의 승인·실패·모름·예외, 1분/24시간 경계, 24시간 초과 제외와 단일 경고, 이미 `PAID`/`FAILED`인 주문의 경합, 한 건 예외 후 나머지 처리, 50건 초과 공정성, PG 호출의 트랜잭션 밖 실행, 중복 복구의 단일 `TOP_UP`, 응답 무결성 불일치를 검증한다.
- 오래 `PENDING`인 주문 정리는 이번 단계에 넣지 않고 `docs/lab/notes/pg-backlog.md`에 둔다.

### 5단계 — 웹훅

**상태: 진행 전**

- `PAYMENT_STATUS_CHANGED`를 수신해 즉시 기록하고 200으로 응답하며, 실제 처리는 분리한다.
- 웹훅 본문의 성공 여부를 신뢰하지 않고 PG 조회 결과로만 DB 2 경로를 호출한다.
- 중복 수신과 이미 `PAID`인 주문을 무시하는 테스트를 추가한다.

### 6단계 — 프론트 충전 화면과 예약 복귀 흐름

**상태: 진행 전**

- 금액 선택 → 서버 충전 주문 생성 → 결제 SDK 호출 → 결제 요청 순서로 진행한다. 성공·실패 URL 페이지에서는 서버 승인 API만 호출하고, 결제 금액은 서버가 반환한 값만 사용한다.
- 최초 구현 프롬프트의 "결제위젯 렌더링"은 현재 확정된 결제창형 설계에 따라 결제창 SDK 호출로 구현한다.
- 예약 결제 확정에서 422 `INSUFFICIENT_BALANCE`를 받으면 "크레딧이 부족합니다. 충전하시겠어요?" 알림창을 표시하고 `[결제하기]`, `[취소]` 버튼을 제공한다.
- 알림창에 HOLD 남은 시간을 보여 준다. HOLD가 만료되면 다시 예약하라고 안내하며 선점 시간을 연장하지 않는다.
- 충전 금액 기본값은 부족분을 100원 단위로 올린 값으로 하되 최소 1,000원으로 한다. 허용 여부의 최종 검증은 서버가 수행한다.
- 충전 후 돌아갈 `returnTo`는 내부 경로만 허용하도록 검증해 오픈 리다이렉트를 방지한다.
- 충전이 끝나도 예약 결제를 자동 실행하지 않는다. 사용자가 예약 화면에서 결제 버튼을 다시 누른다.
