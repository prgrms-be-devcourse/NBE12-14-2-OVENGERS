# 07. 단계별 Kotlin 전환과 정리

**목적:** P0/P1 검증을 통과한 뒤 코드를 작은 도메인 단위로 옮기고, Java 상호 운용 설정을 마지막에 정리한다. [상위 계획](00-kotlin-migration-plan.md)의 P2 작업이다.

## 전환 순서

| 순서 | 후보와 수정 방법 | 필수 검증 |
| --- | --- | --- |
| 1. 순수 정책·유틸 | [`ReservationRefundPolicy`](../../backend/src/main/java/com/ovengers/slotkey/reservation/policy/ReservationRefundPolicy.java), [`ReservationTimePolicy`](../../backend/src/main/java/com/ovengers/slotkey/reservation/policy/ReservationTimePolicy.java), [`SpaceOperatingHoursPolicy`](../../backend/src/main/java/com/ovengers/slotkey/space/policy/SpaceOperatingHoursPolicy.java)처럼 DB·Spring 의존이 작은 파일을 선택한다. Java 정적 호출은 `object`/`@JvmStatic` 또는 호출부 변경으로 맞춘다. | 기존 경계값 테스트, Java 호출부 컴파일. |
| 2. DTO와 비영속 서비스 | [03 API 계약](03-api-contract.md)이 고정된 DTO부터 파일별로 바꾼다. Java `record` 접근자·Lombok builder 사용 지점을 같은 PR에서 수정한다. 서비스는 Kotlin 주 생성자 주입과 기존 `@Transactional` 위치를 유지한다. | MockMvc JSON·검증, Java/Kotlin 컴파일, 서비스 테스트. |
| 3. 컨트롤러·설정 | `@CurrentMember`, `@Valid`, `@ModelAttribute`, `Pageable` 기본값, `@Value`/`@ConfigurationProperties`를 요청·기동 테스트로 확인한다. | 권한, 쿠키, 오류 코드, 프로필별 기동. |
| 4. 엔티티·리포지토리 | [06 JPA 문서](06-jpa-and-proxies.md)의 매핑·프록시 검증을 갖춘 도메인부터 바꾼다. 예약·크레딧·출입은 [02 금전·경합](02-money-and-concurrency.md)의 실 MySQL 검증을 매 PR 실행한다. | 저장/재조회, Auditing, 조건부 UPDATE, 락·동시성. |
| 5. 마무리 | 운영 Java 파일 목록과 Lombok 사용을 다시 세어 남길 파일을 결정한다. 더 이상 쓰지 않는 플러그인·annotation processor만 제거한다. | 전체 빌드, 실제 프로필 기동, API·프론트엔드 검증. |

## 파일 단위 작업 절차

1. 한 PR의 범위를 **하나의 정책 또는 한 도메인의 밀접한 파일 묶음**으로 적고, 해당 Java 호출부·테스트·DTO를 찾는다. 같은 클래스의 `.java`와 `.kt`를 동시에 남기지 않는다.
2. nullable 선언을 DB 컬럼과 요청 계약에 맞춘다. `Optional` 반환이나 Java 플랫폼 타입은 명시적으로 처리한다. `Int`/`Long` 선택은 기존 JSON 숫자·DB 타입과 맞추고, 금액 계산의 오버플로 검증을 보존한다.
3. `record`의 값 비교·접근자, Lombok builder·getter, Java 정적 메서드가 Java 호출부에서 어떻게 보였는지 확인한다. Kotlin `data class`는 DTO에 우선 사용하고 엔티티에서는 [06 문서](06-jpa-and-proxies.md)의 기준을 적용한다.
4. `Clock`을 통한 시간 계산, 오류 코드, 로깅, `@Transactional` 경계를 유지한다. 자동 변환 도구 결과는 수동으로 읽고 Java의 변경 가능 컬렉션·null 의미가 달라지지 않았는지 확인한다.
5. 관련 테스트 → `cd backend && ./gradlew clean build` → API가 닿는 경우 `cd frontend && node --test tests/*.test.cjs && npx tsc --noEmit` 순으로 실행한다. Docker를 사용할 수 없는 환경이라면 실 DB 검증이 필요한 PR의 완료로 판정하지 않는다. PR은 CI `build` 결과를 확인하고, `dev` 반영 뒤에는 의존하는 `deploy` 작업의 JAR 재빌드·기동·정상 응답도 확인한다.
6. PR에 전환한 파일, Java 호출부 수정, 계약 차이 여부, 명령·결과를 기록한다. API 동작이 바뀌면 `docs/api-spec.md`와 `frontend/src/types/api.ts`를 같은 PR에서 갱신한다.

## 완료 판단

- 매 PR은 도메인 테스트·클린 빌드·관련 프론트엔드 검증을 통과한다. 예약·크레딧·출입은 실 MySQL 경합 테스트를 통과한다.
- 전체 완료 시 `Flyway` 적용과 예약→결제→출입→취소 주요 흐름을 다시 확인하고 새 날짜의 결과를 [`docs/test-results.md`](../../docs/test-results.md)에 기록한다.
- 남은 Java 파일과 Lombok/혼용 플러그인은 의도와 근거가 문서화되어 있다. 불필요한 설정은 별도 정리 변경에서 제거한다.
