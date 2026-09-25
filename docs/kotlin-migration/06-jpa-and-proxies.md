# 06. JPA 엔티티와 Spring 프록시

**목적:** Kotlin 엔티티의 생성·상속·필드 매핑과 Spring AOP가 Java 때와 같은 영속성·트랜잭션 동작을 내도록 검증한다. [상위 계획](00-kotlin-migration-plan.md)의 세 번째 P1 작업이다.

## 대상과 위험 지점

운영 코드에 `@Entity` 12개와 [`BaseTimeEntity`](../../backend/src/main/java/com/ovengers/slotkey/global/common/entity/BaseTimeEntity.java) `@MappedSuperclass` 1개가 있다. 우선 [`Member`](../../backend/src/main/java/com/ovengers/slotkey/member/entity/Member.java), [`Reservation`](../../backend/src/main/java/com/ovengers/slotkey/reservation/entity/Reservation.java), [`Space`](../../backend/src/main/java/com/ovengers/slotkey/space/entity/Space.java), [`RefreshToken`](../../backend/src/main/java/com/ovengers/slotkey/auth/entity/RefreshToken.java), [`Inquiry`](../../backend/src/main/java/com/ovengers/slotkey/inquiry/entity/Inquiry.java)를 점검한다. `RefreshToken.member`는 `LAZY`, `Space.version`은 DB `NOT NULL`과 연결된 기본값 0이다. `BaseTimeEntity`와 `AuditLog`는 Auditing을 사용한다.

## 설정과 엔티티 변환 방법

1. [04 혼용 빌드](04-mixed-build.md)에서 선택한 Kotlin 버전의 JPA 플러그인이 무인자 생성자와 `open` 처리를 어떻게 제공하는지 확인한다. Kotlin 2.3.20 이상은 JPA 플러그인이 JPA `all-open` preset도 적용한다. 이전 버전에서는 엔티티 `open` 설정을 별도로 검증한다. Spring `@Service`·`@Configuration`은 `kotlin-spring` 프록시 설정을 확인한다.
2. 엔티티별로 `@Table`, `@Id`, `@GeneratedValue`, `@Column`, `@Enumerated`, 관계·지연 로딩, 상속 필드를 표로 기록한다. Kotlin에서 애너테이션이 필드에 놓이도록 `@field:` 지정 여부를 확인한다. 실제 매핑은 `EntityManager` 저장·flush·clear·재조회로 검증한다.
3. JPA 엔티티는 변경 가능한 상태와 영속성 식별자를 갖는다. 자동 `equals/hashCode/toString`이 연관관계를 순회하거나 미영속/영속 객체 동등성을 바꾸지 않도록 `data class`를 기본 선택으로 사용하지 않는다. 필수 필드는 생성 팩터리에서 보장하되 JPA의 무인자 인스턴스화 경로도 테스트한다.
4. `Space.version = 0`, nullable 시각 필드와 `Member.balance`의 기본값이 신규 생성·DB 재조회에서 유지되는지 확인한다. `RefreshToken.member`는 `entityManager.flush(); entityManager.clear()` 후 프록시 초기화 전/후를 확인해 예기치 않은 즉시 로딩을 찾는다.
5. [`JpaAuditingConfig`](../../backend/src/main/java/com/ovengers/slotkey/global/config/JpaAuditingConfig.java)의 주입 `Clock`을 사용해 생성·수정 시각을 확인한다. [`AuditLogService`](../../backend/src/main/java/com/ovengers/slotkey/audit/service/AuditLogService.java)의 `MANDATORY` 전파와 관리자 변경 실패 시 롤백을 기존 실 DB 테스트로 확인한다. Kotlin 메서드가 final로 남아 AOP를 우회하지 않는지 실제 빈 호출로 검증한다.
6. 설정 클래스와 [`SpaceImageProperties`](../../backend/src/main/java/com/ovengers/slotkey/space/image/SpaceImageProperties.java)의 바인딩을 기동 테스트로 확인한다. 생성자 바인딩으로 바꿀 경우 기본 경로와 환경변수 우선순위를 테스트한다.

## 테스트와 완료 조건

- 각 엔티티 전환 PR에서 `cd backend && ./gradlew test`를 Docker/MySQL 8.4 환경으로 실행한다. 저장·재조회, LAZY 로딩, Auditing, 상태 전이와 롤백을 해당 도메인 테스트로 확인한다.
- `./gradlew bootJar`와 테스트 프로필의 Flyway V1~V10 적용을 확인한다. 엔티티 문법 변경만으로 Flyway 파일을 수정하지 않는다. 실제 스키마 계약이 바뀌면 다음 번호의 마이그레이션을 추가한다.
- 프록시·트랜잭션의 최종 판정은 단위 Mockito 테스트가 아니라 Spring 빈과 실 DB를 거친 통합 테스트에서 한다.

공식 근거: [Spring Boot 3.3 Kotlin 지원](https://docs.spring.io/spring-boot/3.3/reference/features/kotlin.html), [Kotlin JPA 무인자 생성자](https://kotlinlang.org/docs/no-arg-plugin.html), [Kotlin 2.3.20 JPA 변경](https://kotlinlang.org/docs/whatsnew2320.html#improved-jpa-support-in-the-kotlin-plugin-jpa-plugin).
