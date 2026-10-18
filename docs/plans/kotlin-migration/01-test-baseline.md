# 01. 테스트 기준선과 문의 API 검증

**목적:** Kotlin 전환 전 동작을 재실행 가능한 기준선으로 만들고, 문의의 회원 소유권·상태 전이·DB 검증을 보강한다. [상위 계획](00-kotlin-migration-plan.md)의 첫 번째 P0 작업이다. 소스 대조 기준은 2026-10-18 / `16b9ead`이며 아래 기존 테스트는 이번에 재실행하지 않았다.

## 현재 근거와 수정 대상

- [2026-09-24 테스트 기록](../../../docs/testing/test-results.md)은 57개 클래스·354개 테스트 통과를 보고한다. 새 실행 결과는 아직 아니다.
- 테스트 Java 소스는 지원/가짜 구현을 포함해 138개 파일이다. 실행 클래스/케이스 수는 새 빌드의 XML로 집계한다. [테스트 전략](../../testing/test-strategy.md#9-현재-미구현-및-미검증-영역-gap-analysis)의 과거 gap은 아래 대응표와 현재 소스를 먼저 대조한다.
- [`AdminInquiryControllerTest`](../../../backend/src/test/java/com/ovengers/slotkey/inquiry/controller/AdminInquiryControllerTest.java)에 `@Test` 11개가 있다. 실제 보안 필터를 포함한 WebMvcTest로 관리자 목록·필터·기본 페이징·상세/404·답변 위임과 인증/권한을 확인한다. 서비스가 MockBean이므로 영속성 검증은 아니다.
- 빈 테스트 5개는 여전히 `SlotKeyApplicationTests`, `AuthServiceTest`, `MemberAuthorizationServiceTest`, `MockPaymentGatewayTest`, `PaymentServiceTest`다. 뒤의 두 파일은 기존 `payment` 패키지의 빈 클래스이며, 현재 `credit/pg`의 PG 구현·`FakePgClient`·충전 테스트와 구분해 정리 여부를 판단한다. PG 미도입으로 설명하지 않는다.
- 문의 대상: [`InquiryController`](../../../backend/src/main/java/com/ovengers/slotkey/inquiry/controller/InquiryController.java), [`AdminInquiryController`](../../../backend/src/main/java/com/ovengers/slotkey/inquiry/controller/AdminInquiryController.java), [`InquiryService`](../../../backend/src/main/java/com/ovengers/slotkey/inquiry/service/InquiryService.java), [`Inquiry`](../../../backend/src/main/java/com/ovengers/slotkey/inquiry/entity/Inquiry.java).

## 기존 테스트와 보강 작업 대응표

아래의 “재실행”은 테스트 소스가 존재한다는 뜻이다. 각 클래스의 assertion을 확인하고 전환 커밋에서 실행한 뒤 통과 여부를 기록한다. 표에 없는 경로도 변경 파일의 직접 호출부에서 확인한다.

| 영역 | 우선 재실행할 기존 테스트 | 보강/신규 작성 판단 |
| --- | --- | --- |
| 문의 관리자 HTTP | `AdminInquiryControllerTest` | 기존 11개를 재사용. 회원 생성·목록/상세 소유권·수정, 답변/거절의 DB 상태와 입력 검증을 신규/보강한다. |
| 예약 결제·취소·잠금 | `ReservationPaymentConcurrencyTest`, `ReservationCancelConcurrencyTest`, `ReservationCancelConcurrencyIntegrationTest`, `ReservationExtendHoldConcurrencyIntegrationTest`, `MemberWithdrawalHoldConcurrencyIntegrationTest` | 동시 취소의 REFUND 행 수/합계 검증은 이미 있다. 서로 다른 예약의 잔액 초과 동시 차감, 위약금 구간 두 원장 행, 상태·시간 경계의 누락 assertion을 보강한다. |
| PG 충전·복구·웹훅 | `ChargeOrderConfirmIntegrationTest`, `ChargeOrderRecoveryProcessorIntegrationTest`, `TossWebhookIntegrationTest`, `PgWebhookEventProcessorIntegrationTest` | 충전 1회·원장 합계·PG 호출 트랜잭션 부재·확정 실패가 기존 대상이다. 테스트별 경합/실패 조합을 [02](02-money-and-concurrency.md)에 매핑한다. |
| 인증 | `VerificationFlowIntegrationTest`, `EmailSignupIntegrationTest`, `PasswordResetIntegrationTest`, `VerificationMemberStatusIntegrationTest`, `RefreshTokenServiceTest` | 인증번호 동시 확인·잠금, 증표 소비/롤백/목적·이메일 불일치·만료 등 변경 경로의 assertion을 대조하고 빠진 조합만 보강한다. |
| Outbox·대기·알림 | `OutboxRecordIntegrationTest`, `OutboxRelayIntegrationTest`, `WaitlistApplyConcurrencyTest`, `WaitlistEntryLostUpdateTest`, `SlotReleasedListenerTest`, `SlotReleasedHandlerTest`, `SlotReleasedHandlerConcurrencyTest`, `NotificationReadIntegrationTest` | 취소/이벤트 동반 롤백, 중복 처리, 알림 읽음, nullable active와 lost update 검증을 재사용한다. 기존 payload→전환 후 코드 호환 사례를 고정한다. |
| 통계 | `SpaceRankingConfirmServiceIntegrationTest`, `SpaceRankingQueryServiceIntegrationTest`, `SpaceRankingBackfillServiceIntegrationTest`, `StatsControllerIntegrationTest`, `AdminStatsControllerIntegrationTest` | 기간/자정·00:01 경계, long 순매출/JDBC 결과, 재확정·지역 스냅샷을 재사용한다. 일반/관리자 응답을 구분한다. |

클래스는 모두 `backend/src/test/java/com/ovengers/slotkey/` 아래에 있다. 추가·보강이 필요한지는 [02](02-money-and-concurrency.md)·[03](03-api-contract.md)·[06](06-jpa-and-proxies.md)의 계약별로 판정하고, 이미 존재하는 테스트를 신규 작성 수에 넣지 않는다.

## 실행 순서

1. **환경과 결과 고정.** Docker가 실행 중인 JDK 21 환경에서 `cd backend && ./gradlew clean build`를 실행한다. 날짜, Git 커밋, JDK·Docker·MySQL 이미지 버전, 테스트 건수와 실패 원인을 `docs/testing/test-results.md`에 새 기록으로 남긴다. [`backend-ci.yml`](../../../.github/workflows/backend-ci.yml)의 `build` 작업은 `dev`/`main` 대상 PR과 `dev` 푸시에 실행되므로, 같은 커밋의 해당 작업 결과를 로컬 결과와 나란히 기록한다. `deploy` 작업의 `-x test` 결과를 테스트 통과 증거로 세지 않는다.
2. **기존 테스트와 빈 클래스 판정.** 위 대응표에서 재실행/보강/신규 작성 목록을 확정한다. 빈 클래스는 기존 검증과 중복되는지 확인해 목적을 보강하거나 제거한다. 현재 PG 테스트를 빈 payment 클래스와 함께 지우지 않는다. 테스트 수만 늘리기 위한 형식적 메서드는 추가하지 않는다.
3. **문의 검증 보강.** 기존 관리자 WebMvcTest를 재사용하고 `backend/src/test/java/com/ovengers/slotkey/inquiry/`에 빠진 회원 HTTP/보안·서비스/DB 검증을 추가한다. 실 DB 테스트는 기존 `IntegrationTestSupport` 또는 `@ActiveProfiles("test")`와 `MySqlTestContainerConfig`를 사용한다. 답변 시각에는 고정 `Clock`을 사용한다.
4. **응답과 DB 상태를 함께 확인.** `MockMvc`에서 인증·권한·`ApiResponse`를 확인하고, 커밋 후 DB를 재조회한다. 거절된 요청은 저장 부작용이 없어야 한다.
5. **기록 갱신.** 새 테스트의 시나리오를 `docs/testing/test-strategy.md`에 연결하고 전체 실행 결과를 `docs/testing/test-results.md`에 추가한다. 로컬과 CI 결과를 구분한다.

## 문의 기능의 최소 시나리오

| 경로 | 확인할 계약 | 작업 구분 |
| --- | --- | --- |
| `POST /api/v1/inquiries` | 로그인 사용자의 문의가 `WAITING`으로 생성된다. 빈 제목/내용 및 길이 초과는 400이며 저장되지 않는다. | 회원 HTTP·DB 검증 신규 작성 |
| `GET /api/v1/inquiries`, `GET /api/v1/inquiries/{id}` | 본인 항목만 조회된다. 타인 상세는 `FORBIDDEN_NOT_OWNER`, 없는 ID는 `INQUIRY_NOT_FOUND`다. | 회원 소유권·페이징 검증 신규 작성 |
| `PATCH /api/v1/inquiries/{id}` | 본인 `WAITING`만 수정된다. 타인 또는 `ANSWERED`는 거절되고 원문이 유지된다. | 성공/거절 HTTP·DB 검증 신규 작성 |
| `GET /api/v1/admin/inquiries`, `GET /api/v1/admin/inquiries/{id}` | 관리자 목록·필터·기본 page 0/size 20·상세/404, 목록의 일반 회원 403·미인증 401을 유지한다. | 기존 HTTP 테스트 재실행, 실제 DB 필터/권한 누락 보강 |
| `POST /api/v1/admin/inquiries/{id}/answer` | 답변 후 `ANSWERED`·답변자 ID/시각이 저장된다. 일반 회원은 403, 빈 답변은 400이다. | 기존 성공 JSON/위임 재실행, 거절·상태/DB 검증 보강 |

## 완료 조건

- `cd backend && ./gradlew clean build`가 Docker 사용 환경에서 통과하고, 새 실행 기록이 남는다.
- 문의의 권한·검증·상태 전이가 HTTP 응답과 DB 재조회로 확인된다.
- 빈 클래스 5개 각각의 테스트 목적 또는 제거 근거가 정리된다.
- `dev` 푸시 시 `build` 성공 뒤 self-hosted `deploy`가 실행되는지 확인한다. 현재 배포는 clean 재빌드·systemd 재시작·기동 완료 로그 검사다. 이는 기능 HTTP 검증이 아니며 `-x test`를 테스트 통과로 세지 않는다.
- 현재 `spring.test.context.cache.maxSize=2`를 유지하고, Fake PG/메일·Kafka OFF 기반 검증을 실제 외부 서비스/브로커 E2E와 구분한다. 이번 문서 갱신만으로 이 완료 조건을 충족했다고 처리하지 않는다.

다음 작업: [금전·경합](02-money-and-concurrency.md), [API 계약](03-api-contract.md).
