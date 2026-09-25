# 03. API 계약 고정

**목적:** Java `record`와 Lombok DTO를 Kotlin 클래스로 옮겨도 프론트엔드가 받는 HTTP 계약을 유지한다. [상위 계획](00-kotlin-migration-plan.md)의 세 번째 P0 작업이다.

## 계약의 기준 파일

- 공통 응답: [`ApiResponse`](../../backend/src/main/java/com/ovengers/slotkey/global/common/response/ApiResponse.java), [`PageResponse`](../../backend/src/main/java/com/ovengers/slotkey/global/common/response/PageResponse.java), [`GlobalExceptionHandler`](../../backend/src/main/java/com/ovengers/slotkey/global/error/GlobalExceptionHandler.java).
- 바인딩·검증 예: [`SpaceCreateRequest`](../../backend/src/main/java/com/ovengers/slotkey/space/dto/request/SpaceCreateRequest.java), [`SpaceUpdateRequest`](../../backend/src/main/java/com/ovengers/slotkey/space/dto/request/SpaceUpdateRequest.java), [`AdminAuditLogSearchCondition`](../../backend/src/main/java/com/ovengers/slotkey/audit/dto/AdminAuditLogSearchCondition.java), [`ReservationSearchCondition`](../../backend/src/main/java/com/ovengers/slotkey/reservation/dto/request/ReservationSearchCondition.java).
- 소비자와 명세: [`frontend/src/types/api.ts`](../../frontend/src/types/api.ts), [`docs/api-spec.md`](../../docs/api-spec.md), [`frontend/tests`](../../frontend/tests).

## 고정할 계약

| 구간 | 대표 검증과 수정 방법 |
| --- | --- |
| 공통 응답 | 성공/실패의 `status`, `code`, `message`, 페이지의 `content/page/size/totalElements/totalPages`를 JSON 필드 단위로 확인한다. `ApiResponse`의 `@JsonInclude(NON_NULL)` 때문에 최상위 `data == null`일 때 **필드가 생략되는지** 확인한다. 중첩 DTO의 nullable 필드에는 이 규칙을 자동으로 확장하지 않는다. |
| 공간·예약 DTO | 공간 생성/수정, 예약 HOLD/결제/취소의 요청과 응답을 선택한다. `LocalDate`, `LocalTime`의 `HH:mm`, `LocalDateTime`, enum 문자열, `spaceVersion`, `refundAmount`, `penaltyAmount`의 값·`null`을 확인한다. |
| 오류와 인증 | `@Valid` 실패, 없는 ID, 소유권 위반, 권한 없음, 슬롯 충돌의 HTTP 상태와 오류 코드를 고정한다. 로그인/리프레시의 `Set-Cookie` 이름·`HttpOnly`·`Secure`·`SameSite`·경로를 현재 프로필 기준으로 확인한다. |
| 조회 쿼리 | `@ModelAttribute` 검색 조건의 생략/null, 날짜 범위, `Pageable` 기본 크기와 페이지 번호를 실제 MockMvc 요청으로 확인한다. |
| 문의 | 회원 문의 생성·목록·수정과 관리자 답변의 필드명 및 검증 오류를 [01 문서](01-test-baseline.md)의 테스트와 공유한다. |

## 실행 순서

1. `docs/api-spec.md`와 `frontend/src/types/api.ts`에서 실제 사용 엔드포인트를 뽑아 **엔드포인트·요청 DTO·응답 DTO·프론트엔드 타입·테스트** 대응표를 만든다. 명세와 코드가 다르면 현재 HTTP 동작을 먼저 확인하고 차이를 기록한다.
2. 기존 컨트롤러 테스트의 `MockMvc`에 대표 요청을 추가한다. 검증 오류에는 필드 누락과 명시적 `null`을 각각 넣는다. 동적 ID·시각은 입력을 고정하거나 형식과 의미를 검증해 취약한 전체 문자열 스냅샷을 피한다.
3. Kotlin DTO 전환 시 생성자 인자의 nullable 여부와 기본값을 기존 입력 계약에 맞춘다. Bean Validation은 `@field:NotBlank`, 계산된 `is...` 검증은 필요에 따라 `@get:AssertTrue`처럼 실제 읽는 위치를 지정한다. `@JsonFormat`·OpenAPI 애너테이션도 직렬화/문서 결과로 확인한다.
4. Java `record` 접근자 `name()`과 Kotlin 프로퍼티 getter의 JVM 호출 형태가 달라질 수 있으므로 Java 호출부를 함께 컴파일한다. 공개 JSON이 같아도 Java 소스 호환이 자동으로 유지되지는 않는다.
5. 백엔드 변경마다 `cd frontend && node --test tests/*.test.cjs && npx tsc --noEmit`을 실행한다. 현재 [`backend-ci.yml`](../../.github/workflows/backend-ci.yml)의 `build`·`deploy` 어느 작업에도 프론트엔드 검증이 없으므로, 이 결과는 PR 기록에 별도로 남긴다. 계약이 바뀌면 백엔드 DTO·프론트엔드 타입·`docs/api-spec.md`를 같은 변경에서 갱신한다.

## 완료 조건

- 대표 경로에 요청 유효/무효, 응답 JSON, 오류 코드/상태의 테스트가 있다.
- DTO 전환 전후 계약 테스트 결과가 같다. 의도된 계약 변경만 별도 변경 내역에 명시한다.
- 프론트엔드 타입 검사와 Node 테스트가 통과한다. 공개 타입이나 화면 동작이 바뀐 PR은 `npm run build`도 실행한다.

이후 작업: [혼용 빌드](04-mixed-build.md), [단계별 코드 전환](07-code-migration.md).
