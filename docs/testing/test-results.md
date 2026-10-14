# 테스트 결과 보고서

> **문서 목적**: 로컬 및 CI 환경에서 실행된 백엔드 자동화 테스트의 실측 결과를 기록하고, 도메인별 검증 현황과 미실행/미검증 범위를 명확히 추적한다.

---

## 1. 최신 로컬 전체 테스트 실행 결과 (2026-09-24)

- **실행 일시**: 2026-09-24 12:16 KST
- **실행 명령**:
  ```bash
  JAVA_HOME=/opt/homebrew/opt/openjdk ./gradlew test --rerun-tasks --no-daemon
  ```
- **실행 환경**: Mac OS X (aarch64), JDK 21 (OpenJDK 21.0.12.1 Toolchain), Docker MySQL 8.4 Testcontainers
- **실행 소요 시간**: 2분 7초 (`BUILD SUCCESSFUL in 2m 7s`)
- **실행 결과 요약**:
  - **테스트 소스 클래스**: 62개 (지원 클래스 5개 제외, 유효 57개 + 빈 클래스 5개)
  - **실행된 XML 테스트 클래스**: **57개**
  - **총 실행 테스트 케이스**: **354개**
  - **실패 (Failures)**: **0**
  - **에러 (Errors)**: **0**
  - **스킵 (Skipped)**: **0**
  - **최종 판정**: **전원 통과 (ALL PASSED)**

### 도메인별 테스트 현황 (2026-09-24 실측)

| 도메인 | 클래스 수 | 테스트 수 | 주요 검증 범위 |
| --- | --- | --- | --- |
| **reservation** | 21 | 124 | HOLD 임시 확보, 결제, 연장, 취소, 체크아웃, 만료 스케줄러, 노쇼 배치, 슬롯 동시성(단일 슬롯, 인접 슬롯, 부분 겹침, 취소 경합, 멱등 결제 경합) |
| **space** | 15 | 112 | 공간 CRUD, 운영시간 정책, 슬롯 가용성, 관리자 보안 필터, 대표 사진 파일 저장/롤백, 5MB 크기 제한(413 `IMAGE_SIZE_EXCEEDED`), 이미지 스트리밍 GET 200, OpenAPI/Swagger 문서화 |
| **access** | 5 | 33 | 출입 토큰 발급/검증/폐기, 시간 경계(시작 15분 전/후), 소유권 인가, 동시 발급 경합 |
| **audit** | 6 | 25 | 감사 로그 단위 기록(`AuditLogServiceTest`), `MANDATORY` 트랜잭션 전파(`AuditLogServiceTransactionTest`), 관리자 감사 로그 페이징 조회 API, 보안 인가 체인, 필터 쿼리 (※ 회원 상태·크레딧 동반 커밋 및 감사 실패 시 비즈니스 롤백은 `member` 패키지의 `AdminMemberServiceIntegrationTest`에서 검증, 비즈니스 예외 시 선행 감사 롤백은 미작성) |
| **credit** | 3 | 22 | 관리자 크레딧 지급, 크레딧 거래 단위 생성(`balanceAfter` 검증), 회원 상태 변경과 크레딧 증가 동시 경합 시 최신 잔액 보존 (동시 차감 경합 및 SUM 무결성 DB 검증은 미구현) |
| **auth** | 2 | 19 | 로그인/로그아웃, Refresh 재발급, 정지 계정 토큰 만료 전 API 허용 및 재발급 차단 |
| **member** | 2 | 16 | 관리자 회원 목록/상세 조회, 상태 변경(ACTIVE/SUSPENDED), 자기 자신 크레딧 지급 거절(`SELF_GRANT_NOT_ALLOWED`), 인가 필터, 회원 변경·크레딧 지급과 감사 로그 동반 커밋 및 감사 저장 실패(SpyBean 예외 주입) 시 비즈니스 롤백 실 DB 검증(`AdminMemberServiceIntegrationTest`) |
| **global** | 3 | 3 | CORS 설정, JPA Auditing, JwtProvider 토큰 생성 및 파싱 |
| **합계** | **57** | **354** | **실패 0 / 에러 0 / 스킵 0** |

---

## 2. 빈 테스트 클래스 (미실행 5개)

다음 5개 클래스는 소스 코드가 존재하지만 내부 `@Test` 메서드가 없는 빈 클래스(`public class ... {}`)로, JUnit 5 실행 대상에서 제외되어 XML 결과가 생성되지 않았습니다:

1. `com.ovengers.slotkey.SlotKeyApplicationTests`: 스프링 부트 기본 컨텍스트 로드 테스트 클래스 (미작성 상태)
2. `com.ovengers.slotkey.auth.service.AuthServiceTest`: 인증 서비스 단위 테스트 (통합 컨트롤러 테스트 `AuthControllerTest`에서 대체 검증 중)
3. `com.ovengers.slotkey.member.authorization.MemberAuthorizationServiceTest`: 회원 인가 서비스 단위 테스트
4. `com.ovengers.slotkey.payment.gateway.MockPaymentGatewayTest`: PG 연동 모의 테스트 (PG 미도입, 크레딧 단일화로 정리 대상)
5. `com.ovengers.slotkey.payment.service.PaymentServiceTest`: 결제 서비스 단위 테스트 (크레딧 단일화로 정리 대상)

---

## 3. 과거 로컬 실행 기록 (2026-09-22 스냅샷 보존)

> 다음 수치는 2026-09-22 당시 로컬 실행 결과(`backend/build/test-results/test/*.xml`, 실행 시각 2026-09-21 03:29 UTC)의 역사적 기록 스냅샷입니다.

- **테스트 소스 클래스**: 55개, `@Test` 메서드 298개
- **당시 실행 클래스/테스트**: 42개 클래스, 258개 테스트 (실패 0 / 에러 0 / 스킵 0)
- **당시 미실행 클래스 (13개)**: 9/21~9/22 추가되었던 감사 로그(4), 동시성(4), 관리자 회원(2), 결제(2), 인증(1) 관련 테스트들.
- **2026-09-24 재실행 비교**: 당시 미실행 13개 중 유효 테스트를 가진 8개 클래스 및 추가된 공간 이미지 관련 4개 클래스를 포함하여 57개 클래스, 354개 테스트로 전체 정상 통합 실행 완료됨.

---

## 4. 알려진 제약 및 미검증 범위

1. **Docker 및 Testcontainers 의존성**:
   - 비관적 락, UNIQUE 제약 및 동시성 통합 테스트는 실제 Docker MySQL 8.4 컨테이너가 필수적입니다. Docker 데몬이 구동되지 않는 환경에서는 실행이 불가합니다.
2. **회원 1:1 문의 (Inquiry) 백엔드 자동화 테스트 부재**:
   - V10 마이그레이션 및 문의 API/화면은 구현되었으나, 백엔드 전용 단위/통합 테스트는 아직 작성되지 않은 미검증 상태입니다.
3. **프론트엔드 자동화 테스트의 범위와 한계**:
   - 프론트엔드는 Node.js 내장 test runner(`node --test tests/*.test.cjs`)를 통해 컴포넌트 정적 렌더링, 시간 포맷팅(`HH:mm`), 검색 필터, 문의 API 연동 규격을 검증하는 **14개 테스트가 통과**되어 있습니다 (이 14개는 백엔드 JUnit XML의 354개 수치와 별개로 집계).
   - 반면 Jest/Vitest 통합 테스트 환경, Playwright/Cypress 브라우저 E2E 테스트, 실제 브라우저 UI 상호작용 자동화 검증은 부재합니다.
4. **CI 및 외부 부하 테스트 (k6) / 브라우저 Swagger UI**:
   - 본 수치는 로컬 전체 실행 실측치이며, k6 부하 테스트 및 브라우저 환경에서의 Swagger UI multipart 업로드 실사용은 별도 미실시 상태입니다.
5. **세부 복합 시나리오 통합 테스트 부재**:
   - 상태 전이 불가 6종 조합 전수 매트릭스 검증, 사용자 일반 취소의 동일 예약 2회 순차 재취소 검증, 이용 시작 시각 정각/경과 후 취소 시도 거절 시간 경계 검증, 취소 시 REFUND·PENALTY 원장 2건 동시 영속성 검증, 관리자 강제 취소 ↔ 사용자 연장 동시 경합(데드락 부재) 검증, 비즈니스 로직 예외 발생 시 감사 로그 동반 롤백 검증, 스케줄러 자동 체크아웃의 `checked_out_at = end_time` DB 영속성 검증, 노쇼 시 크레딧 잔액 미환불 DB 영속성 검증, 일반 회원의 관리자 크레딧 지급 API(`POST /api/v1/admin/members/{memberId}/credits`) 호출 차단 검증, 타인 예약 조회 API 소유권 검증, 가격 변경 후 기존 확정 예약 조회 시 원래 금액 유지 복합 시나리오 검증은 엔티티/조건부 UPDATE/권한 필터 설계로 방어되나 전용 통합 테스트는 미작성 상태입니다.
