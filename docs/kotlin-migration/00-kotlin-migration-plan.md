# 백엔드 Kotlin 마이그레이션 준비 계획

> 작성 기준: 2026-09-25 현재 저장소. CI 검토 기준은 `54f76e4` 커밋이다. 대상은 `backend/`의 Java 21·Spring Boot API이며, `frontend/`는 API 계약 검증 대상이다. 아래 테스트 수치는 이번 작업에서 재실행한 결과가 아니라 [2026-09-24 테스트 결과 기록](../test-results.md)이다.

## 현재 위치와 판단

- `backend/build.gradle`은 Java 단일 모듈이다. Spring Boot 3.3.4, Gradle Wrapper 8.10.2, Java toolchain 21을 사용하며 Kotlin 플러그인과 Kotlin 소스는 없다.
- 운영 코드는 Java 파일 165개 중 JPA `@Entity` 12개와 `@MappedSuperclass` 1개, `record` 45개, Lombok 사용 파일 70개를 포함한다. 따라서 문법 변환보다 **Java/Kotlin 혼용 컴파일, JPA 프록시, 요청 바인딩·검증, API 직렬화**가 전환 위험의 중심이다.
- MySQL 8.4 Testcontainers와 Flyway V1~V10을 사용하고 `ddl-auto: none`으로 스키마를 관리한다. 예약·크레딧·출입 경합은 조건부 UPDATE, DB 제약 및 락에 의존한다. Kotlin 전환 자체에는 스키마 변경이 필요하지 않다.
- [테스트 결과 기록](../test-results.md)은 2026-09-24 로컬 실행에서 57개 테스트 클래스·354개 테스트 통과를 보고한다. 이 기록은 새 환경의 통과를 보증하지 않는다. [테스트 전략](../test-strategy.md)에는 문의 API 테스트 부재, 동시 차감과 일부 상태·권한 경계 미검증이 남아 있다.
- [backend-ci.yml](../../.github/workflows/backend-ci.yml)은 `dev`/`main` 대상 PR 및 `dev` 푸시에서 Ubuntu 빌드 작업으로 `./gradlew build`를 실행한다. `dev` 푸시에서는 빌드 성공 후 self-hosted 배포 작업이 `./gradlew build -x test`로 JAR를 다시 만들고 앱을 재시작한다. 현재 배포 확인은 HTTP 응답 유무만 판별한다. 프론트엔드 계약 검증 단계는 없다.

**권장 방향:** 먼저 비교 가능한 테스트·API 기준선을 확보하고, Java/Kotlin 혼용 빌드를 작은 변경으로 검증한다. 그다음 의존 관계가 적은 영역부터 옮기고 예약·크레딧·출입 및 JPA 엔티티는 실 DB 회귀 검증이 갖춰진 뒤 진행한다. 첫 전환 단계에서는 Boot 3.3.4, Gradle 8.10.2, Java 21을 고정하고 프레임워크 버전 변경은 별도 작업으로 다룬다.

## 작업별 실행 문서

1. [P0 — 테스트 기준선과 문의 API 검증](01-test-baseline.md)
2. [P0 — 금전·예약 동시성 검증](02-money-and-concurrency.md)
3. [P0 — API 계약 고정](03-api-contract.md)
4. [P1 — Java/Kotlin 혼용 빌드](04-mixed-build.md)
5. [P1 — Lombok 상호 운용 경계](05-lombok-boundary.md)
6. [P1 — JPA 엔티티와 Spring 프록시](06-jpa-and-proxies.md)
7. [P2 — 단계별 Kotlin 전환과 정리](07-code-migration.md)

각 문서는 해당 작업의 수정 대상, 순서, 검증 명령 및 완료 조건을 설명한다. P0 세 작업은 서로 병행할 수 있으며, P1 혼용 빌드의 최종 통과 기준은 P0 기준선에 의존한다.

## 우선순위별 준비 작업

| 우선순위 | 수정할 부분과 근거 | 완료 기준 |
| --- | --- | --- |
| P0 — 기준선 | 현재 `./gradlew build`를 Docker가 동작하는 환경에서 재실행하고 결과를 날짜·환경·명령과 함께 기록한다. 빈 테스트 클래스 5개는 실제 검증을 추가하거나 정리한다. 문의 API의 요청 검증·본인/관리자 권한·응답 계약 테스트를 우선 추가한다. | 동일 커밋의 전체 테스트가 로컬과 CI에서 통과하며, 실패 시 어느 계약이 달라졌는지 알 수 있다. |
| P0 — 금전·경합 | [테스트 전략의 미검증 목록](../test-strategy.md#9-현재-미구현-및-미검증-영역-gap-analysis) 중 동시 차감 음수 방지와 거래 원장 일치, 취소 환불·위약금 영속화, 예약 상태 전이 경계를 먼저 보강한다. `ReservationRepository`, `ReservationSlotRepository`, `CreditBalanceRepository`의 조건부 쿼리·락은 변경 전후 동일 결과를 확인한다. | 실 MySQL 통합 테스트에서 성공 건수, 잔액·원장, 예약·슬롯 상태와 시간 경계가 일치한다. |
| P0 — API 계약 | `record` DTO 및 `frontend/src/types/api.ts`를 대조해 대표 요청·응답 JSON을 테스트로 고정한다. 필수 필드 누락/명시적 `null`, 날짜·시간 형식, `ApiResponse`의 `data: null` 생략, 에러 코드·HTTP 상태, 로그인 쿠키를 포함한다. | Kotlin DTO 또는 컨트롤러 전환 전후에 같은 페이로드·검증 오류가 나온다. 프론트엔드의 `node --test tests/*.test.cjs`와 타입 검사도 통과한다. |
| P1 — 혼용 빌드 | 기존 `build.gradle`에 Kotlin JVM·Spring·JPA 플러그인과 `kotlin-reflect`, Jackson Kotlin 모듈을 추가하는 작은 준비 PR을 만든다. Kotlin 플러그인 버전은 착수 시 Boot 3.3 계열과 Gradle 8.10.2의 호환 범위를 공식 문서로 확인해 하나로 고정한다. `src/main/kotlin`, `src/test/kotlin`을 열고 Java↔Kotlin 호출을 각각 컴파일 검증한다. | `compileJava`, `compileKotlin`, `test`, `bootJar`가 통과하고 애플리케이션이 현재 프로필로 시작한다. |
| P1 — Lombok 경계 | Lombok 생성 getter·builder·생성자를 Kotlin 소스가 같은 모듈에서 참조하는 지점을 조사한다. 기존 Java 코드를 모두 미리 바꿀 필요는 없다. 첫 전환 단위의 직접 의존을 명시적 Java 메서드/생성자로 정리하거나 Kotlin Lombok 컴파일러 플러그인을 제한적으로 검증해 선택한다. | 선택한 경계에서 클린 빌드가 안정적으로 되고, Java 테스트 호출부와 Kotlin 호출부가 함께 컴파일된다. |
| P1 — JPA·Spring 프록시 | `Member`, `Reservation`, `Space`, `RefreshToken` 등을 엔티티 전환 파일 목록으로 만들고 생성 방식·지연 로딩·감사 시각·기본값을 확인한다. Kotlin 엔티티에는 무인자 생성자와 프록시 가능한 클래스/메서드가 필요하다. `@Transactional` 서비스와 `@Configuration`도 프록시 적용을 확인한다. | 엔티티 저장·재조회·지연 로딩, Auditing, 트랜잭션 롤백·전파가 실 DB 테스트에서 유지된다. |
| P2 — 코드 단순화 | 순수 정책/값 객체부터 Kotlin으로 옮기고, Java `record` 접근자(`name()`)와 Lombok builder 호출부를 파일별로 갱신한다. `data class`는 DTO에 우선 적용하고 JPA 엔티티에는 자동 `equals/hashCode/toString` 위험을 검토한 후 사용한다. | 각 도메인 묶음이 독립적으로 빌드·테스트되고 공개 API와 Flyway 스키마가 유지된다. |

## 권장 실행 순서: 전환 시작까지 몇 주

| 시점 | 작업 묶음 | 산출물과 다음 단계 조건 |
| --- | --- | --- |
| 시작 3~2주 전 | P0 테스트 기준선 재실행, 문의 API 및 금전·경합의 핵심 미검증 시나리오 추가. 대표 API JSON과 프론트엔드 타입의 차이를 정리한다. | 테스트 기록 갱신, 추가된 통합/HTTP 계약 테스트, 미해결 차이 목록. |
| 시작 2~1주 전 | 별도 준비 PR에서 혼용 빌드와 짧은 파일 하나를 시범 전환한다. Kotlin 플러그인·라이브러리 버전을 확정하고 CI의 빌드 작업 및 배포 작업의 JAR 생성 경로를 확인한다. | Java↔Kotlin 양방향 컴파일, `bootJar`, MySQL 테스트 통과. self-hosted 배포 작업에서 Kotlin 런타임을 포함한 JAR가 생성·기동됨을 확인한다. 컴파일이 불안정하면 범위 확장 보류. |
| 시작 1주 전 | 도메인별 전환 순서와 담당 파일을 정한다. DTO JSON, `@field:`/`@get:` 검증 애너테이션, `@ModelAttribute`, 설정 바인딩을 작은 HTTP 테스트로 확인한다. | 첫 전환 PR의 파일 목록·테스트 목록·롤백 경계가 정해진다. |
| 전환 시작 후 | 순수 정책/유틸 → 서비스·컨트롤러와 DTO → JPA 엔티티·리포지토리 순으로 작은 PR을 만든다. 예약·크레딧·출입은 각 PR마다 실 MySQL 동시성 테스트를 실행한다. | PR마다 전체 백엔드 빌드, 관련 통합 테스트, 프론트엔드 계약 검증을 통과한다. |

## 전환 중 지켜야 할 구현 기준

1. **엔티티:** `@Entity`와 상속된 `BaseTimeEntity`의 필드 매핑을 유지한다. `kotlin-jpa`의 무인자 생성자와 엔티티의 `open` 처리 방식을 선택한 Kotlin 버전에서 확인한다. Kotlin 2.3.20부터 JPA 플러그인이 `all-open` JPA preset도 적용하므로, 버전 확정 전에는 별도 설정 필요 여부를 단정하지 않는다. `Space.version`의 기본값 0, nullable 시각 필드, `RefreshToken.member`의 지연 로딩을 별도 확인한다.
2. **DTO와 검증:** Java `record`를 Kotlin으로 바꿀 때 JSON 필드명과 생성자 기본값, 누락과 `null`의 차이를 명시한다. Bean Validation·Jackson·OpenAPI 애너테이션은 `@field:`, `@get:`, `@param:` 대상 위치를 의도대로 지정하고 MockMvc로 검증한다. `SpaceCreateRequest`의 `@AssertTrue`와 `AdminAuditLogSearchCondition`·`ReservationSearchCondition`의 `@ModelAttribute` 바인딩을 우선 확인한다.
3. **트랜잭션과 동시성:** `@Transactional(readOnly = true)` 클래스와 쓰기 메서드, 감사 로그의 `MANDATORY` 전파, 예약의 조건부 UPDATE 영향 행 검사, `@Lock`을 그대로 보존한다. Kotlin nullable 타입 도입으로 기존 `Optional`/nullable 조회 결과를 무심코 단정형으로 바꾸지 않는다. 시간 계산은 기존 주입 `Clock`과 `LocalDateTime` 경계를 유지한다.
4. **설정과 실행:** `SpaceImageProperties`의 기본 저장 경로, JWT·CORS 설정, `@Configuration` 빈 생성과 Spring Security 필터를 기동 테스트로 확인한다. Gradle Groovy DSL을 Kotlin DSL로 바꾸는 일은 언어 전환의 필수 조건이 아니므로 별도 단계로 잡는다.
5. **프론트엔드 계약:** 공개 URL, 응답 래퍼, 오류 코드, 날짜·시간 문자열과 쿠키는 기존 `docs/api-spec.md` 및 `frontend/src/types/api.ts`와 일치시킨다. 계약 변경이 실제로 필요한 경우 백엔드·프론트엔드·문서를 같은 PR에서 갱신한다.

## 착수·완료 판단

- **착수 가능:** 전체 테스트 최신 결과가 기록되어 있고, P0 핵심 미검증 시나리오와 대표 HTTP 계약 테스트가 추가되었으며, 혼용 빌드 시범 PR이 CI에서 통과한다.
- **각 전환 PR 완료:** 클린 `./gradlew build`와 관련 실 MySQL 테스트 통과, 직렬화·검증·권한·트랜잭션 차이 없음, 변경된 공개 계약은 프론트엔드 테스트와 문서에 반영됨. `dev`에 반영되는 변경은 배포 작업의 JAR 재빌드·기동 결과도 확인한다.
- **전체 전환 완료:** 운영 Java 소스가 의도한 범위만 남고 Lombok/혼용 보조 설정의 필요성을 재평가한다. Flyway 적용과 실제 프로필 기동, 주요 예약→결제→출입→취소 흐름을 다시 확인한다. 결과는 `docs/test-results.md`에 새 실행 날짜로 기록한다.

## 참고 근거

- 저장소: [`backend/build.gradle`](../../backend/build.gradle), [`backend-ci.yml`](../../.github/workflows/backend-ci.yml), [테스트 전략](../test-strategy.md), [테스트 결과](../test-results.md), [핵심 도메인 결정](../core-domain-decisions.md).
- 공식 문서: [Spring Boot 3.3 Kotlin 지원](https://docs.spring.io/spring-boot/3.3/reference/features/kotlin.html), [Kotlin JPA 무인자 생성자](https://kotlinlang.org/docs/no-arg-plugin.html), [Kotlin 2.3.20 JPA `all-open` 변경](https://kotlinlang.org/docs/whatsnew2320.html#improved-jpa-support-in-the-kotlin-plugin-jpa-plugin), [Kotlin Lombok 혼용 플러그인](https://kotlinlang.org/docs/lombok.html).
