# 05. Lombok 상호 운용 경계

**목적:** Java의 Lombok 생성 API를 Kotlin 컴파일러와 Java 호출부가 안정적으로 사용하도록 전환 단위를 정한다. [상위 계획](00-kotlin-migration-plan.md)의 두 번째 P1 작업이다.

## 현재 경계

운영 Java 파일 70개가 Lombok을 import한다. [`Space`](../../backend/src/main/java/com/ovengers/slotkey/space/entity/Space.java), [`Reservation`](../../backend/src/main/java/com/ovengers/slotkey/reservation/entity/Reservation.java), [`Inquiry`](../../backend/src/main/java/com/ovengers/slotkey/inquiry/entity/Inquiry.java)는 builder·getter·무인자 생성자에 의존한다. [`SpaceResponse`](../../backend/src/main/java/com/ovengers/slotkey/space/dto/response/SpaceResponse.java)는 `record`이지만 Lombok builder를 사용한다. 서비스/컨트롤러의 `@RequiredArgsConstructor`와 테스트의 `@InjectMocks`도 호출 형태에 영향을 준다.

## 조사와 수정 방법

1. **인벤토리 작성.** `rg -l 'import lombok' backend/src/main/java`로 파일을 추출하고 `엔티티`, `DTO`, `서비스/컨트롤러`, `기타`로 분류한다. 첫 Kotlin 전환 후보가 호출하는 Lombok getter·builder·생성자와 Java 테스트의 호출부를 표시한다. 70개 파일을 한 번에 고치지 않는다.
2. **경계별 해결 선택.** 직접 의존이 적으면 Java 쪽에 명시적 생성자/팩터리·메서드를 추가해 Kotlin 호출부가 안정적인 JVM API를 보게 한다. 클래스 자체를 Kotlin으로 옮길 때는 주 생성자와 필요한 팩터리로 대체하고 Java 호출부를 같은 PR에서 수정한다. 같은 모듈의 Java Lombok 생성 멤버를 Kotlin 소스가 대량 참조해야 한다면 공식 [Kotlin Lombok 컴파일러 플러그인](https://kotlinlang.org/docs/lombok.html)을 작은 시범 파일에서 검증한 뒤 채택 여부를 정한다.
3. **Java 호환 확인.** `Space.builder()`와 `SpaceResponse.builder()`, `Reservation.createHeld()`, 테스트의 `getXxx()` 호출을 검색한다. Kotlin `data class` 프로퍼티는 Java `record`의 `name()` 접근자를 그대로 만들지 않으므로 [03 계약 문서](03-api-contract.md)에 따라 호출부를 함께 수정한다.
4. **Spring 주입 전환.** Lombok `@RequiredArgsConstructor`를 제거하는 파일은 Kotlin 주 생성자에 모든 의존성을 `private val`로 선언한다. Bean 생성 및 `@Transactional` 프록시 테스트를 실행한다. Java Mockito `@InjectMocks` 테스트는 생성자 변경 후 클린 컴파일과 실제 객체 생성까지 확인한다.
5. **의존성 정리 시점.** Kotlin으로 옮긴 파일의 Lombok 애너테이션·import를 제거한다. 남은 Java 파일이 Lombok을 사용하면 Gradle의 Java annotation processor는 유지한다. 마지막 Java Lombok 의존이 사라진 시점에만 빌드 의존성을 제거한다.

## 선택 기준과 검증

| 선택 | 적용 조건 | 확인할 점 |
| --- | --- | --- |
| 명시적 Java API | 소수의 Kotlin 호출부가 Java Lombok 생성 API를 참조 | Java/Kotlin 양쪽 컴파일, 기존 builder 사용 테스트 유지 |
| 파일 단위 Kotlin 전환 | 해당 클래스와 직접 Java 호출부를 같은 PR에서 갱신 가능 | 생성자·접근자 변경, 직렬화·JPA 동작 및 테스트 수정 |
| Kotlin Lombok 플러그인 | 혼용 기간에 대량의 같은 모듈 Lombok 호출이 불가피 | 선택 버전 지원 애너테이션, `clean compileKotlin compileJava`, CI 재현성 |

`kapt`나 다른 annotation processor를 나중에 추가한다면 Java Lombok 처리 단계가 계속 실행되는지 공식 플러그인 문서와 클린 빌드로 재확인한다. 컴파일 오류를 숨기려고 생성 API를 전역적으로 바꾸지 않는다.

## 완료 조건

- 첫 전환 범위의 Lombok 경계가 목록화되고 해결 방식이 파일별로 정해진다.
- `cd backend && ./gradlew clean compileKotlin compileJava test`가 통과한다.
- Java 테스트의 builder/getter 사용과 Kotlin 호출이 모두 컴파일·동작한다. 남은 Lombok 의존성은 빌드에 유지된다.
