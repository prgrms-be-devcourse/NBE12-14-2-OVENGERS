# 테스트 결과 보고서

> **문서 목적**: 로컬 및 CI 환경에서 실행된 백엔드 자동화 테스트의 실측 결과를 기록하고, 도메인별 검증 현황과 미실행/미검증 범위를 명확히 추적한다.
>
> **2026-10-18 안내 (`16b9ead`)**: 아래 2026-09-24/09-22 수치와 제약·“PG 미도입/문의 테스트 부재” 설명은 당시 실행의 스냅샷이다. 이후 PG 충전·관리자 문의 HTTP 테스트 등이 추가됐으며 현재 소스 대응표는 [Kotlin 전환 테스트 기준선](../plans/kotlin-migration/01-test-baseline.md)을 참조한다. 당시에는 빌드·테스트를 재실행하지 않았으나, 이후 2026-10-20 기능 동결 기준선 실행을 **§0-0**에 추가했다.

> **현재 HEAD 실행 결과: 2026-10-20 기준선 확인** — 기능 동결 커밋 `d1a15eca3228c8b1b1f9260fc7a686860d2b1e44`(짧은 해시 `d1a15ec`)에서 `./gradlew clean build`를 실행해 **1048개 테스트 전원 통과(실패 0 / 오류 0 / 스킵 0)**를 확인했다. 상세는 아래 **§0-0**을 참조한다. 2026-10-18의 "미확인" 안내와 아래 2026-09-24/09-22 수치는 당시 실행의 스냅샷이며 현재 HEAD 결과가 아니다.

---

## 0-0. 기능 동결 기준선 실행 기록 (2026-10-20, 로컬)

> 화·수 계획과 목요일 혼용 빌드 시범 전의 **기능 동결(Feature Freeze) 기준선**이다. 아래 수치는 로컬 실측이며 실제 배포 환경 검증과 별개다. 이번 실행은 대단위 1 범위이며 Kotlin 코드·프로덕션 로직은 변경하지 않았다.

| 항목 | 기록 |
| --- | --- |
| 실행 일시 | 2026-10-20 15:50~16:02 KST (테스트 단계) |
| 대상 | `dev` 브랜치, 기능 동결 커밋 `d1a15eca3228c8b1b1f9260fc7a686860d2b1e44` (짧은 해시 `d1a15ec`) |
| 실행 명령 | `env -u SPRING_MAIL_USERNAME -u SPRING_MAIL_PASSWORD -u VERIFICATION_MAIL_FROM -u SLOTKEY_KAFKA_ENABLED ./gradlew --no-daemon clean build` |
| 실행 환경 | Linux, OpenJDK 21.0.12.1 (`/usr/lib/jvm/java-21-openjdk`), Gradle 8.10.2, Docker 29.8.2 + MySQL 8.4 Testcontainers |
| 전체 빌드 | **`BUILD SUCCESSFUL in 11m 44s`**, 종료 코드 `0` |
| 테스트 단계 소요 | 11분 19초 (XML 첫 타임스탬프 15:50:42 ~ 마지막 16:02:01 KST) |
| 실행 테스트 클래스(스위트) | **135** |
| 총 실행 테스트 메서드 | **1048** |
| 통과 | **1048** |
| 실패 | **0** |
| 오류 | **0** |
| 스킵 | **0** |
| `@Disabled` | **0건** (테스트 소스 전체) |
| 최종 판정 | **전원 통과 (ALL PASSED)** |

### 기준선 해석과 이전 기록과의 차이

- 테스트 소스 파일은 148개다. 이 중 실제 실행 스위트는 135개, `@Test`가 없는 빈 클래스 5개, 지원·픽스처 클래스 8개(`IntegrationTestSupport`, `ReservationIntegrationTestSupport`, `TestFixture`, `FakePgClient`, `FakeOutboxPublisher`, `FixedClockConfig`, `MySqlTestContainerConfig`, `SettableClockConfig`)다.
- `V2SecurityRedTest`는 15개 테스트가 모두 활성 상태로 통과했고 `@Disabled`는 0건이다. 2026-10-15 CI에서 실패했던 11건은 현재 소스에서 활성 회귀 안전망으로 동작한다.
- Auth V2 관련 실행 스위트는 `AuthControllerTest`(25), `V2SecurityRedTest`(15), `AuthSessionMigrationIntegrationTest`, `LoginRateLimitIntegrationTest`, `RefreshTokenServiceTest`, `PasswordResetIntegrationTest`, `EmailSignupIntegrationTest`, `VerificationFlowIntegrationTest`, `VerificationMemberStatusIntegrationTest` 등이다.
- `16b9ead..d1a15ec` 사이 추가된 테스트 클래스는 10개다: `InactiveMemberAccessIntegrationTest`, `AuthSessionMigrationIntegrationTest`, `LoginRateLimitIntegrationTest`, `CreditTransactionRepositoryTest`, `GlobalExceptionHandlerTest`, `InactiveMemberAccessFilterTest`, `AdminMemberDetailControllerTest`, `AdminMemberDetailServiceTest`, `ReservationControllerTest`, `ReservationMemberCountRepositoryTest`. 같은 기간 `AuthControllerTest`, `V2SecurityRedTest`, `RefreshTokenServiceTest`, `AdminMemberControllerTest`, `AdminMemberServiceIntegrationTest` 등이 보강됐다.

### 실행 환경 주의 (재현 조건)

이 로컬 셸에는 로컬 기동용 `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, `VERIFICATION_MAIL_FROM`, `SLOTKEY_KAFKA_ENABLED=true`가 내보내져 있었다. 이 값들은 테스트 프로필(`application-test.yml`)의 빈 SMTP 값과 `slotkey.kafka.enabled=false` 기본값을 덮어써 다음 두 유형의 실패를 만든다.

1. `SmtpVerificationMailSender` 생성자의 `VERIFICATION_MAIL_FROM must match SPRING_MAIL_USERNAME` 위반으로 444건 컨텍스트 로드 실패.
2. `KafkaConsumerDisabledTest`, `OutboxRelayDisabledTest`의 "기본 비활성" 빈 부재 단언 실패 2건.

따라서 **테스트 기준선은 위 네 환경 변수를 해제한 뒤 실행**해야 한다. 처음 두 실행에서 관측한 실패는 소스 회귀가 아니라 실행 셸 환경 변수에 의한 것이며, 해제 후 전체 1048건이 통과했다. 테스트 프로필이 외부 SMTP·Kafka 환경 변수에 영향받지 않도록 격리하는 후속 개선은 이번 기준선 범위 밖이다.

---

## 0-1. V2 1번 이슈 실행 기록 (2026-10-18, 로컬)

> 구현은 이후 브랜치 `auth/improvement/v2`의 커밋 `e671c475be90c90804712a1866e708bc7d145432`로 고정됐다(로컬 커밋 완료, 미Push·미병합·미배포). 아래는 커밋 전 작업 트리(기준 `16b9ead` + V2 1번 변경)에서 Codex가 실행한 결과를 리뷰어가 JUnit XML로 다시 집계한 것이다. dev HEAD의 결과가 아니며, 이 문서 갱신 중에 재실행하지 않았다. 원시 결과는 임시 디렉터리에 있어 영구 보관되지 않는다.

| 실행 | 시각 (KST) | 전체 | 통과 | 실패 | 오류 | 건너뜀 |
| --- | --- | --- | --- | --- | --- | --- |
| 수정 전 첫 전체 실행 | 15:27:54~15:34:11 | 995 | 936 | 49 | 0 | 10 |
| 최종 전체 실행 | 15:37:25~15:43:38 | 995 | 985 | 0 | 0 | 10 |
| UTC 선택 실행 (5개 클래스, 테스트 JVM `user.timezone=UTC`) | 16:01:12~16:01:36 | 68 | 68 | 0 | 0 | 0 |
| 마지막 보완 이후 `AuthControllerTest` 선택 실행 | 16:10:16 | 25 | 25 | 0 | 0 | 0 |
| **2번 이슈** 관련 6개 클래스 선택 실행 (미커밋 작업 트리) | 16:44:20~16:44:56 | 37 | 33 | 0 | 0 | 4 |
| **2번 이슈** 전체 실행 (126개 스위트, 미커밋 작업 트리) | 16:45:20~16:51:52 | 999 | 995 | 0 | 0 | 4 |
| **3번 이슈** 관련 5개 클래스 선택 실행 (미커밋 작업 트리) | 17:25:26~17:26:13 | 47 | 46 | 0 | 0 | 1 |
| **3번 이슈** 마이그레이션 테스트 수정 후 `AuthSessionMigrationIntegrationTest` (미커밋 작업 트리) | 17:36:53 | 2 | 2 | 0 | 0 | 0 |
| **4번 이슈** 선택 실행 (`LoginRateLimitIntegrationTest` 7, `AuthSessionMigrationIntegrationTest` 2, `AuthControllerTest` 3개 메서드, `V2SecurityRedTest` 1개 메서드; 미커밋 작업 트리) | 17:55:25~17:56:02 | 13 | 13 | 0 | 0 | 0 |
| **4번 이슈** 영향 클래스 전체 실행 (`AuthControllerTest` 25, `V2SecurityRedTest` 15, `PasswordResetControllerTest` 1; 미커밋 작업 트리) | 18:04:04~18:04:28 | 41 | 41 | 0 | 0 | 0 |

- 집계 기준: XML `tests`에 건너뜀이 포함된다(통과 = tests − failures − errors − skipped).
- 첫 실행의 실패 49개: `AdminStatsControllerIntegrationTest` 35, `ReservationSearchSecurityIntegrationTest` 9, `StatsControllerIntegrationTest` 5.
- 건너뜀 10개는 모두 `V2SecurityRedTest`의 `@Disabled`(V2 2~4번 이슈 범위). 상세는 [인증 보안 테스트](auth-security-tests.md).
- 그 전에 보고된 987/871/116/10 실행은 아티팩트가 없어 확인하지 못했다.
- UTC 선택 실행은 `JwtProviderTest`가 출력한 `AUTH_TEST_JVM_ZONE_ID=UTC`로 테스트 JVM 시간대를 확인했다. 전체 실행 이후 테스트 4개의 이름·주석·시간대 기록이 바뀌었고, 그중 `AdminSpaceSecurityIntegrationTest`(이름만 변경)는 바뀐 뒤 다시 실행되지 않았다.
- 995개 전체 실행과 UTC 68개 선택 실행은 마지막 예외 처리 보완(`TransactionException`) **이전** 코드 기준이다. 보완 이후에는 `AuthControllerTest` 25개 선택 실행만 통과했다(2026-10-18 16:10 KST, 커밋 직전 작업 트리). 보완 이후 전체 실행은 없다.
- 2번 이슈 두 실행은 1번 커밋 위의 미커밋 2번 변경 기준이며, 마지막 소스 수정(16:43:39) 이후 실행이다(파일 시각 기준). 남은 `@Disabled` 4개: RT 재사용 불가·RT Rotation 후 계속 갱신·동일 RT 동시 사용 1회 성공(3번 이슈), 반복 로그인 실패 429(4번 이슈).
- 3번 이슈 실행은 마지막 소스 수정(17:24:32) 이후다. 남은 `@Disabled` 1개는 반복 로그인 실패 429(4번 이슈). `AuthSessionMigrationIntegrationTest`의 Flyway 버전 단언 차단 사항은 수정 후 단독 실행(2개 통과)으로 해결됐다. 두 3번 실행은 별도 선택 실행이며 합쳐서 전체 회귀 통과로 보지 않는다. `V2SecurityRedTest`는 이후 표시 이름·주석만 바뀌고 다시 실행되지 않았다. **3번 변경 이후 전체 테스트는 실행하지 않았다.**
- V2 4번 이슈는 로컬 구현 커밋 `ee39f91`(미Push·미병합·미배포)으로 고정됐다. 첫 실행은 메서드 단위 선택 실행이었고, 이후 영향 클래스 3개를 클래스 전체로 실행해 41개 통과했다(마지막 소스 수정 17:55:03 이후). 두 실행은 별도 기록이다. `V2SecurityRedTest` 15개 전체 통과는 이 클래스의 결과이며, **4번 변경 뒤 전체 백엔드 테스트는 실행하지 않았다.** 테스트 소스에 남은 `@Disabled`는 없다.
- 이 결과는 로컬 테스트 근거이며 실제 배포 환경 검증과는 별개다.

---

## 0. V1 인증 고도화 PR 실행 기록 (2026-10-15, CI)

> 출처: [인증 보안 테스트](auth-security-tests.md) §2, [V1 인증 문서 지도](../plans/auth/README.md) §5. 테스트별 의미와 V2 통과 기준은 `auth-security-tests.md`를 단일 기준으로 둔다.

| 항목 | 기록 |
| --- | --- |
| 대상 | PR #302 head, 브랜치 `auth/v1-frontend-integration`, 커밋 `5c60fa444dfc427016954fdacc44d3dfd293c004` |
| 실행 | GitHub Actions 백엔드 CI — [실행 #38684148319](https://github.com/prgrms-be-devcourse/NBE12-14-2-OVENGERS/actions/runs/38684148319) |
| 결과 | 전체 694 / 통과 682 / **실패 11** / 건너뜀 1 |
| 실패 내용 | 11개 모두 `com.ovengers.slotkey.auth.controller.V2SecurityRedTest`의 의도적 보안 단언 실패(로그아웃·재설정·정지·탈퇴 후 기존 토큰 거부, RT 재사용·회전·동시 사용, 반복 로그인 실패 429). 일반 회귀 실패는 기록되지 않음 |
| 함께 기록된 V1 회귀 | `PasswordResetIntegrationTest` 5개 통과, `EmailSignupIntegrationTest`·`VerificationMemberStatusIntegrationTest`는 실패 목록에 없음 |

**당시 실행과 현재 설정의 구분**

| 구분 | 커밋 | `V2SecurityRedTest` 11개 |
| --- | --- | --- |
| 위 CI 실행 | PR head `5c60fa4` | `@Disabled` 없음 → 11개 실패 |
| 현재 소스 | dev 병합본 `46526a5` 이후 (HEAD `16b9ead`까지 확인) | 11개 모두 `@Disabled("V2 구현 전까지 CI에서 비활성화")` → 실행 시 건너뜀으로 집계 |

병합 후의 전체 실행 수치는 기능별 결정 문서에 기록돼 있다(예: [outbox-kafka.md](../decisions/outbox-kafka.md)의 `./gradlew cleanTest test` 814 tests, 실패 0, 건너뜀 11 — 11건은 `V2SecurityRedTest`의 `@Disabled`). 이 기록들은 각 기능 작업 시점의 결과이며 현재 HEAD 결과가 아니다. 따라서 694/682/11/1을 현재 결과로 쓰지 않는다.

---

## 1. 로컬 전체 테스트 실행 결과 (2026-09-24 기록)

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
| **auth** | 2 | 19 | 로그인/로그아웃, Refresh 재발급, 정지 계정 토큰 만료 전 API 허용 및 재발급 차단 (2026-09-24 시점. V1 이메일 인증 테스트는 이후 PR #302에서 추가 — §0) |
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
