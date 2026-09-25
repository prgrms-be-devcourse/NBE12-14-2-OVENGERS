# 01. 테스트 기준선과 문의 API 검증

**목적:** Kotlin 전환 전 동작을 재실행 가능한 기준선으로 만들고, 현재 테스트가 없는 문의 기능을 보호한다. [상위 계획](00-kotlin-migration-plan.md)의 첫 번째 P0 작업이다.

## 현재 근거와 수정 대상

- [2026-09-24 테스트 기록](../../docs/test-results.md)은 57개 클래스·354개 테스트 통과를 보고한다. 새 실행 결과는 아직 아니다.
- [테스트 전략](../../docs/test-strategy.md#9-현재-미구현-및-미검증-영역-gap-analysis)은 문의 API 전용 백엔드 테스트 부재를 명시한다.
- 빈 테스트 5개: `SlotKeyApplicationTests`, `AuthServiceTest`, `MemberAuthorizationServiceTest`, `MockPaymentGatewayTest`, `PaymentServiceTest`. PG 관련 두 클래스는 현재 크레딧 결제 범위와 무관한 흔적으로 기록되어 있다.
- 문의 대상: [`InquiryController`](../../backend/src/main/java/com/ovengers/slotkey/inquiry/controller/InquiryController.java), [`AdminInquiryController`](../../backend/src/main/java/com/ovengers/slotkey/inquiry/controller/AdminInquiryController.java), [`InquiryService`](../../backend/src/main/java/com/ovengers/slotkey/inquiry/service/InquiryService.java), [`Inquiry`](../../backend/src/main/java/com/ovengers/slotkey/inquiry/entity/Inquiry.java).

## 실행 순서

1. **환경과 결과 고정.** Docker가 실행 중인 JDK 21 환경에서 `cd backend && ./gradlew clean build`를 실행한다. 날짜, Git 커밋, JDK·Docker·MySQL 이미지 버전, 테스트 건수와 실패 원인을 `docs/test-results.md`에 새 기록으로 남긴다. [`backend-ci.yml`](../../.github/workflows/backend-ci.yml)의 `build` 작업은 `dev`/`main` 대상 PR과 `dev` 푸시에 실행되므로, 같은 커밋의 해당 작업 결과를 로컬 결과와 나란히 기록한다. `deploy` 작업의 `-x test` 결과를 테스트 통과 증거로 세지 않는다.
2. **빈 클래스 판정.** `SlotKeyApplicationTests`에는 실질적인 기동 검증을 추가하거나 기존 통합 테스트의 커버 여부를 확인하고 제거한다. `AuthServiceTest`와 `MemberAuthorizationServiceTest`는 기존 컨트롤러/인가 테스트와 규칙을 매핑하고 빠진 규칙만 작성한다. 사용하지 않는 PG 관련 두 클래스는 제품 범위를 확인한 뒤 정리한다. 테스트 수만 늘리기 위한 형식적 메서드는 추가하지 않는다.
3. **문의 테스트 추가.** `backend/src/test/java/com/ovengers/slotkey/inquiry/`에 서비스·HTTP/보안 통합 테스트를 둔다. 실 DB 테스트는 기존 `IntegrationTestSupport` 또는 `@ActiveProfiles("test")`와 `MySqlTestContainerConfig`를 사용한다. 답변 시각에는 기존 고정 `Clock` 패턴을 사용한다.
4. **응답과 DB 상태를 함께 확인.** `MockMvc`에서 인증·권한·`ApiResponse`를 확인하고, 커밋 후 DB를 재조회한다. 거절된 요청은 저장 부작용이 없어야 한다.
5. **기록 갱신.** 새 테스트의 시나리오를 `docs/test-strategy.md`에 연결하고 전체 실행 결과를 `docs/test-results.md`에 추가한다. 로컬과 CI 결과를 구분한다.

## 문의 기능의 최소 시나리오

| 경로 | 확인할 계약 |
| --- | --- |
| `POST /api/v1/inquiries` | 로그인 사용자의 문의가 `WAITING`으로 생성된다. 빈 제목/내용 및 길이 초과는 400이며 저장되지 않는다. |
| `GET /api/v1/inquiries`, `GET /api/v1/inquiries/{id}` | 본인 항목만 조회된다. 타인 상세는 `FORBIDDEN_NOT_OWNER`, 없는 ID는 `INQUIRY_NOT_FOUND`. 페이지 래퍼와 기본 크기를 확인한다. |
| `PATCH /api/v1/inquiries/{id}` | 본인 `WAITING` 문의만 수정된다. 타인 또는 `ANSWERED` 문의는 거절되고 원문이 유지된다. |
| `GET /api/v1/admin/inquiries`, `GET /api/v1/admin/inquiries/{id}` | 일반 회원은 403. 관리자는 전체 목록과 상태 필터를 사용한다. |
| `POST /api/v1/admin/inquiries/{id}/answer` | 관리자 답변 후 `ANSWERED`, 답변자 ID·시각이 저장된다. 일반 회원은 403이고 빈 답변은 400이다. |

## 완료 조건

- `cd backend && ./gradlew clean build`가 Docker 사용 환경에서 통과하고, 새 실행 기록이 남는다.
- 문의의 권한·검증·상태 전이가 HTTP 응답과 DB 재조회로 확인된다.
- 빈 클래스 5개 각각의 테스트 목적 또는 제거 근거가 정리된다.
- `dev` 푸시 시 `build` 성공 뒤 self-hosted `deploy`가 실행되는지 확인하고, 빌드 실패 시 배포가 시작되지 않았는지 확인한다. 현재 HTTP 응답 유무 검사는 애플리케이션 기능 검증으로 집계하지 않는다.

다음 작업: [금전·경합](02-money-and-concurrency.md), [API 계약](03-api-contract.md).
