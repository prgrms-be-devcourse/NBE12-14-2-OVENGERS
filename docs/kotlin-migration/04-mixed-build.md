# 04. Java/Kotlin 혼용 빌드

**목적:** Java 파일을 유지한 상태에서 Kotlin 소스·테스트를 빌드하고 패키징할 수 있는 최소 설정을 확정한다. [상위 계획](00-kotlin-migration-plan.md)의 첫 번째 P1 작업이다. [01](01-test-baseline.md)과 [03](03-api-contract.md)의 기준선이 있어야 동작 변화를 판별할 수 있다.

## 현재 설정과 결정 사항

[`backend/build.gradle`](../../backend/build.gradle)은 `java`, Spring Boot 3.3.4, dependency-management 1.1.6과 Java toolchain 21을 사용한다. Wrapper는 Gradle 8.10.2이고 Lombok은 Java의 `compileOnly`/`annotationProcessor`로 설정되어 있다. 첫 준비 PR에서는 이 버전과 Groovy DSL을 유지한다.

착수 시 공식 [Spring Boot 3.3 Kotlin 지원](https://docs.spring.io/spring-boot/3.3/reference/features/kotlin.html), [Gradle 호환성 표](https://docs.gradle.org/current/userguide/compatibility.html), [Kotlin JPA 플러그인](https://kotlinlang.org/docs/no-arg-plugin.html)을 확인해 **사용할 Kotlin Gradle 플러그인 버전 하나**를 기록한다. 이 문서는 미래의 플러그인 버전을 미리 고정하지 않는다.

## 빌드 설정 수정 순서

1. `plugins`에 `org.jetbrains.kotlin.jvm`, `org.jetbrains.kotlin.plugin.spring`, `org.jetbrains.kotlin.plugin.jpa`를 같은 검증 버전으로 추가한다. `java { toolchain ... 21 }`와 Kotlin JVM toolchain을 21로 맞춘다. 엔티티를 아직 Kotlin으로 옮기지 않더라도 JPA 플러그인의 설정·컴파일 성공을 확인한다.
2. `dependencies`에 `org.jetbrains.kotlin:kotlin-reflect`와 `com.fasterxml.jackson.module:jackson-module-kotlin`을 추가한다. Spring Boot 관리 버전과 Kotlin 플러그인 버전이 충돌하지 않는지 `dependencyInsight`로 점검한다. Java Lombok 의존성은 혼용 기간에 유지한다.
3. `src/main/kotlin`과 `src/test/kotlin`을 사용한다. 저장소의 Java 패키지 이름 `com.ovengers.slotkey`는 그대로 둔다. Kotlin 파일을 Java 소스 디렉터리로 옮기지 않는다.
4. 첫 시범 파일은 의존이 적고 행위 테스트가 이미 있는 정책/유틸 하나로 정한다. 예를 들어 `ReservationRefundPolicy`를 고르면 Java의 정적 호출자가 있으므로 Kotlin `object`의 `@JvmStatic` 또는 Java 호출부 수정까지 한 PR에 포함한다. 같은 이름의 Java·Kotlin 클래스를 동시에 두지 않는다. 기존 정책 테스트가 통과해야 한다.
5. Java 테스트에서 새 Kotlin 클래스를 호출하고, Kotlin 테스트에서 기존 Java 클래스를 호출해 양방향 경계를 확인한다. Lombok 생성 멤버를 Kotlin이 직접 참조하는 경우는 [05 문서](05-lombok-boundary.md)의 선택을 적용한다.
6. [`backend-ci.yml`](../../.github/workflows/backend-ci.yml)의 Ubuntu `build` 작업은 PR 및 `dev` 푸시에 `./gradlew build`를 실행한다. Kotlin 전환 PR에서 `compileKotlin`·테스트·`bootJar`가 이 경로로 통과하는지 확인한다. `dev` 푸시 후 self-hosted `deploy`는 `./gradlew build -x test`로 JAR를 **다시 빌드**하므로, 이 실행에서도 Kotlin 소스와 런타임이 포함되고 앱이 기동되는지 확인한다. 오래 남은 클래스가 배포 JAR에 섞이지 않도록 배포 작업의 클린 빌드 또는 테스트된 JAR 아티팩트 전달 방식을 CI 변경 작업으로 검토한다.

## 검증 명령과 관찰 지점

```bash
cd backend
./gradlew clean compileKotlin compileJava test bootJar
./gradlew dependencyInsight --dependency kotlin-reflect --configuration runtimeClasspath
```

`bootJar` 안에 Kotlin 클래스와 `kotlin-reflect`, Jackson Kotlin 모듈이 포함되는지 확인한다. 테스트 프로필은 Testcontainers에 연결되고 Flyway V1~V10이 적용되어야 한다. 로컬 프로필 기동은 MySQL과 `JWT_SECRET_KEY`를 준비한 뒤 `./gradlew bootRun`으로 확인한다. 실제 환경변수 값은 문서나 커밋에 남기지 않는다.

## 완료 조건

- 로컬 클린 빌드와 CI `build` 작업에서 Java→Kotlin·Kotlin→Java 호출, 테스트, `bootJar`가 통과한다. 현재 CI 명령은 `clean`을 포함하지 않으므로 클린 빌드 증거는 로컬 명령 결과로 별도 기록한다.
- `dev` 배포 작업이 새 Kotlin 포함 JAR를 빌드·기동한다. 현재 `curl` 검사는 HTTP 상태가 `000`만 아니면 성공하므로, Kotlin 전환 검증에는 공개 `GET /api/v1/spaces`의 200 응답처럼 애플리케이션 기능을 확인하는 배포 후 점검을 추가한다.
- 현재 API 계약과 DB 스키마가 유지되고, 시범 파일의 기존 테스트가 통과한다.
- 선택한 Kotlin 버전과 호환성 확인 근거가 PR에 기록된다. Spring Boot·Gradle 업그레이드는 별도 변경으로 남긴다.

다음 작업: [Lombok 경계](05-lombok-boundary.md), [JPA·Spring 프록시](06-jpa-and-proxies.md).
