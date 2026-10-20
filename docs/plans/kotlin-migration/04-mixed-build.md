# 04. Java/Kotlin 혼용 빌드

**목적:** Java 파일을 유지한 상태에서 Kotlin 소스·테스트를 빌드하고 패키징할 수 있는 최소 설정을 확정한다. [상위 계획](00-kotlin-migration-plan.md)의 첫 번째 P1 작업이다. [01](01-test-baseline.md)과 [03](03-api-contract.md)의 기준선이 있어야 동작 변화를 판별할 수 있다. 2026-10-20 `d1a15ec` 기준선(1,048/1,048)은 확보됐고, 이 문서는 목요일 첫 PR인 **`kotlin-migration/step-0-mixed-build`**의 범위를 정한다.

## 현재 설정과 결정 사항

2026-10-20 / `d1a15ec`의 [`backend/build.gradle`](../../../backend/build.gradle)은 `java`, Spring Boot 3.3.4, dependency-management 1.1.6과 Java toolchain 21을 사용한다. Wrapper는 Gradle 8.10.2이고 Lombok은 Java의 `compileOnly`/`annotationProcessor`다. Mail·Spring Kafka가 추가됐으며 테스트의 **`spring.test.context.cache.maxSize=2`**는 Testcontainers MySQL OOM 대응이므로 유지한다. 첫 준비 PR에서는 기존 버전과 Groovy DSL을 유지한다.

Kotlin Gradle 플러그인은 **`1.9.25` 하나로 고정**한다(Spring Boot 3.3.4 BOM·Java 21 toolchain 정합). 선택 근거·기각 대안·재검토 조건은 [Kotlin 플러그인 버전 결정](../../decisions/kotlin-plugin-version.md)을 따른다. 이 문서에서 미래 버전을 다시 추정하지 않는다.

## `kotlin-migration/step-0-mixed-build` 범위

목요일 첫 PR은 혼용 빌드만 연다. 도메인 코드를 대량 전환하지 않는다.

1. `plugins`에 `org.jetbrains.kotlin.jvm`, `org.jetbrains.kotlin.plugin.spring`, `org.jetbrains.kotlin.plugin.jpa`를 `1.9.25`로 추가하고, `jvmToolchain(21)`·`-Xjsr305=strict`를 설정한다.
2. `kotlin-reflect`, `jackson-module-kotlin`을 추가하고 `src/main/kotlin`, `src/test/kotlin`을 연다. 패키지 `com.ovengers.slotkey`는 유지한다.
3. 시범 파일 1개는 [`ReservationRefundPolicy`](../../../backend/src/main/java/com/ovengers/slotkey/reservation/policy/ReservationRefundPolicy.java)로 한다. Java 정적 호출자 [`ReservationCancelService`](../../../backend/src/main/java/com/ovengers/slotkey/reservation/service/ReservationCancelService.java)가 있으므로 Kotlin `object`의 `@JvmStatic`을 쓰거나 호출부를 같은 PR에서 수정한다. 같은 이름의 `.java`와 `.kt`를 동시에 두지 않는다.
4. 시범 전환 PR에서 `ReservationRefundPolicyTest`를 추가해 경계값(정확히 1시간 전, 1시간 전 직후, 시작 정각, 홀수 금액의 정수 나눗셈)을 직접 고정한다. 기존 `ReservationCancelServiceTest`, `ReservationCancelConcurrencyTest`도 재실행한다.
5. Java 테스트에서 Kotlin 클래스를, Kotlin 테스트에서 기존 Java 클래스를 호출해 양방향 컴파일을 확인한다.

이 PR의 롤백은 단일 revert로 Java 원본을 복구하는 것이다. 스키마·API·직렬화·메시지 형식을 바꾸지 않는다. 본격 도메인 전환은 3차 새 레포에서 [07](07-code-migration.md)의 `step-2` 이후로 진행한다.

## 빌드 설정 수정 순서

1. `plugins`에 `org.jetbrains.kotlin.jvm`, `org.jetbrains.kotlin.plugin.spring`, `org.jetbrains.kotlin.plugin.jpa`를 같은 검증 버전(`1.9.25`)으로 추가한다. `java { toolchain ... 21 }`와 Kotlin JVM toolchain을 21로 맞춘다. 엔티티를 아직 Kotlin으로 옮기지 않더라도 JPA 플러그인의 설정·컴파일 성공을 확인한다.
2. `dependencies`에 `org.jetbrains.kotlin:kotlin-reflect`와 `com.fasterxml.jackson.module:jackson-module-kotlin`을 추가한다. Spring Boot 관리 버전과 Kotlin 플러그인 버전이 충돌하지 않는지 `dependencyInsight`로 점검한다. Java Lombok 의존성은 혼용 기간에 유지한다.
3. `src/main/kotlin`과 `src/test/kotlin`을 사용한다. 저장소의 Java 패키지 이름 `com.ovengers.slotkey`는 그대로 둔다. Kotlin 파일을 Java 소스 디렉터리로 옮기지 않는다.
4. 첫 시범 파일은 의존이 적고 행위 테스트가 이미 있는 정책/유틸 하나로 정한다. `step-0`에서는 `ReservationRefundPolicy`를 선택하며, Java의 정적 호출자가 있으므로 Kotlin `object`의 `@JvmStatic` 또는 Java 호출부 수정까지 한 PR에 포함한다. 같은 이름의 Java·Kotlin 클래스를 동시에 두지 않는다. 기존 서비스·동시성 테스트와 새 `ReservationRefundPolicyTest`가 통과해야 한다.
5. Java 테스트에서 새 Kotlin 클래스를 호출하고, Kotlin 테스트에서 기존 Java 클래스를 호출해 양방향 경계를 확인한다. Lombok 생성 멤버를 Kotlin이 직접 참조하는 경우는 [05 문서](05-lombok-boundary.md)의 선택을 적용한다.
6. [`backend-ci.yml`](../../../.github/workflows/backend-ci.yml)의 Ubuntu `build`는 PR 및 `dev` 푸시에서 `./gradlew build`를 실행한다. `dev`의 self-hosted `deploy`는 이미 **`./gradlew clean build -x test`**로 JAR를 다시 빌드하므로 Kotlin 클래스·런타임 포함 여부를 양쪽에서 확인한다. JAR 설치 후 systemd로 재시작하고 서비스 활성 상태·이번 재시작 이후의 기동 완료 로그를 검사한다. 테스트된 JAR 전달로 바꾸려면 별도 CI 변경으로 다루며, 배포 clean 도입을 미완료 작업으로 적지 않는다.

## 검증 명령과 관찰 지점

```bash
cd backend
./gradlew clean compileKotlin compileJava test bootJar
./gradlew dependencyInsight --dependency kotlin-reflect --configuration runtimeClasspath
./gradlew clean build
```

`bootJar` 안에 Kotlin 클래스·stdlib·`kotlin-reflect`·Jackson Kotlin 모듈이 포함되는지 확인한다. 의존성 조회는 선택 버전의 stdlib/reflect와 Boot가 관리하는 버전의 실제 해석 결과도 확인한다. 테스트는 MySQL 8.4 Testcontainers에 연결되고 **V1~V26 전체** Flyway가 적용되어야 한다. 착수 커밋에 migration이 더 있으면 그 목록 전체를 기준으로 삼고 기존 SQL/checksum은 바꾸지 않는다.

## 프로필·설정별 기동 검증

현재 설정의 기준은 [`application.yml`](../../../backend/src/main/resources/application.yml), [`application-local.yml`](../../../backend/src/main/resources/application-local.yml), [테스트 설정](../../../backend/src/test/resources/application-test.yml), [환경변수 예시](../../../backend/.env.example)다. 실제 비밀값은 문서/커밋에 남기지 않는다.

| 경로 | 준비·관찰할 조건 |
| --- | --- |
| local/prod | MySQL/JWT 외에 `VERIFICATION_HASH_SECRET`(Base64, 32바이트 이상), `VERIFICATION_MAIL_FROM`, SMTP 계정/비밀번호를 준비한다. local/prod의 자격 정보 검증과 발신 주소=SMTP 계정 조건을 유지한다. 로컬은 `./gradlew bootRun --args='--spring.profiles.active=local'`로 확인한다. |
| test | 공개 테스트 설정값·메일 대역·FakePgClient를 사용하고 실제 메일/PG 호출과 분리한다. `TossPaymentsPgConfiguration`은 `!test`이며 테스트용 PG 빈은 별도 설정으로 공급된다. 복구/웹훅 스케줄러는 test에서 OFF다. |
| Toss 설정 | `TossPaymentsProperties`의 URI·Duration·secret 바인딩과 마스킹된 toString을 유지한다. `TossPaymentsPropertiesTest`, `TossPaymentsPgConfigurationTest`, `PgClientTestProfileIntegrationTest`를 재실행한다. 실제 승인 검증에는 별도 샌드박스 설정이 필요하다. |
| Kafka OFF/ON | 기본 OFF에서는 브로커 없이 relay/listener 빈이 제외되는지 확인한다. ON에서는 별도 브로커 설정으로 생성·전송/소비를 확인하고 DB 테스트의 Fake publisher/직접 handler 결과와 구분한다. |
| 스케줄러 | `slotkey.scheduler.charge-recovery.enabled`·`slotkey.scheduler.pg-webhook.enabled`와 통계 스케줄러 조건을 확인한다. 단순 기동과 예약 작업이 실제 동작한 결과는 따로 기록한다. |

공유 ObjectMapper에 Kotlin 모듈이 추가된 뒤 기존 Java HTTP·PG·Outbox JSON도 [03](03-api-contract.md)의 계약 테스트로 재확인한다. 생성자 기본값/nullable 설정이 새 Kotlin DTO뿐 아니라 기존 호출 경로에 주는 영향도 확인한다.

## 완료 조건

- 로컬의 필터 없는 `clean build`와 CI `build`에서 양방향 컴파일·전체 테스트·패키징이 통과한다. Ubuntu `build`에는 clean이 없고 self-hosted `deploy`에는 있다는 차이를 기록한다.
- `kotlin-migration/step-0-mixed-build`는 Kotlin 플러그인 3종·`kotlin-reflect`·Jackson Kotlin 모듈·`src/*/kotlin` 디렉터리와 `ReservationRefundPolicy` 시범 1개, `ReservationRefundPolicyTest`만 포함한다. 단일 revert로 Java 원본을 복구할 수 있어야 한다.
- `dev` 배포의 Kotlin 포함 JAR와 systemd 재기동·기동 로그를 확인한다. 기능 검증은 별도로 공개 `GET /api/v1/spaces`의 200 등 정상 응답을 확인하고, 로그 검사만으로 기능 통과를 주장하지 않는다.
- 현재 API 계약과 DB 스키마가 유지되고, 시범 파일의 기존 테스트가 통과한다.
- 선택한 Kotlin 버전(`1.9.25`)과 호환성 확인 근거가 [결정 문서](../../decisions/kotlin-plugin-version.md)와 PR에 기록된다. Spring Boot·Gradle 업그레이드는 별도 변경으로 남긴다.

다음 작업: [Lombok 경계](05-lombok-boundary.md), [JPA·Spring 프록시](06-jpa-and-proxies.md).
