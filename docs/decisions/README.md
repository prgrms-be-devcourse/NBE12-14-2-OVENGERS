# 의사결정 기록 상태표

기능·기술 선택·정책별 의사결정 기록입니다. 새 문서는 [`TEMPLATE.md`](TEMPLATE.md)의 순서(문제 → 선택과 이유 → 기각한 대안 → Trade-off → 검증 → 한계·재검토 조건)로 쓰고, 면접·발표용 정리를 덧붙입니다. 기존 문서는 문제 → 대안 → 선택과 근거 → 검증 형식이며, 새 형식으로 옮기는 것은 필수가 아닙니다. 범위·순서·일정은 [`plans/`](../plans/)에 두고 이유와 trade-off만 이곳에 씁니다.
상태는 문서 서두의 상태 표기를 1순위로, 없으면 본문 근거(확정 서술·실존 테스트 참조·"구현 전" 명시)로 판단했습니다.

| 파일 | 유형 | 한 줄 요약 | 상태 |
|---|---|---|---|
| [`access-key-policy.md`](access-key-policy.md) | 정책 | 출입 토큰은 예약당 활성 1개로 DB 제약으로 수렴시키고, 최초 체크인은 `[start, start+15분]`으로 제한한다. | 채택 |
| [`authentication-policy.md`](authentication-policy.md) | 정책 | 이메일 인증번호, grant 기반 가입/복구, 단계별 차단, 정지, 로그인 토큰 생명주기의 V1 적용 정책과 V2, V3 방향을 정리한다. 예약 소유권 등 인가는 `authorization-policy.md`가 담당한다. | V1, V2 적용 / V3 Redis 실험은 도입 보류(2026-10-21) / 세부 미결정 항목 존재 |
| [`authorization-policy.md`](authorization-policy.md) | 정책 | 인증·인가를 인증·역할·소유권·상태·감사 다섯 축으로 나누고, 무상태 JWT의 즉시 차단 한계는 별도 과제로 분리한다. | 채택 |
| [`member-withdrawal-policy.md`](member-withdrawal-policy.md) | 정책 | 본인 회원탈퇴의 진행 예약·잔여 크레딧·출입 권한 처리와 재가입 기준을 확정한다. | 채택 |
| [`reservation-concurrency.md`](reservation-concurrency.md) | 기술 선택 | `Space → Reservation` 잠금 순서와 조건부 UPDATE로 결제·연장·취소 경합을 막는다. | 채택 |
| [`reservation-idempotency.md`](reservation-idempotency.md) | 기술 선택 | `Idempotency-Key`를 결제 확정 지점에 걸어 크레딧 차감·예약 확정의 중복을 막는다. | 채택 |
| [`pg-charge.md`](pg-charge.md) | 기능 | 토스페이먼츠 테스트 키로 주문서형 SDK와 서버 승인 API를 붙여 크레딧 충전 경로를 만든다. | 채택 (구현 중 — 6단계 완료, 5단계 웹훅 미착수) |
| [`outbox-kafka.md`](outbox-kafka.md) | 기술 선택 | 도메인 사실을 outbox에 적고 Kafka로 전달해 대기열·알림·감사가 받아 쓰게 한다. 10/21 범위: 폴링 Outbox, `SlotReleased` 1종, 멱등 컨슈머. | 제안 (10/21 범위 결정 반영, 구현 전) |
| [`waitlist.md`](waitlist.md) | 기능 | 예약 대기열을 만든다. 10/21 범위는 빈자리 이벤트 시 대기자 전원 알림(선착순)이고, 1순위 HOLD 제안은 3차다. | 제안 (10/21 범위 결정 반영, 구현 전) |
| [`space-ranking.md`](space-ranking.md) | 기능 | 베스트 공간 랭킹은 끝난 기간만 매일 00:01에 확정값으로 저장하고, 매출은 환불을 환불일에 반영하는 현금주의로 센다. | 구현 완료 (사용자 화면 확인 전, 자기검증) |
| [`kotlin-plugin-version.md`](kotlin-plugin-version.md) | 기술 선택 | Spring Boot 3.3.4 BOM과 맞춰 Kotlin 플러그인·stdlib·reflect를 `1.9.25`로 고정하고, Gradle 8.10.2 호환성 리스크는 `step-0`에서 검증한다. | 채택 (구현 전) |
| [`kotlin-lombok-boundary.md`](kotlin-lombok-boundary.md) | 기술 선택 | Kotlin Lombok 컴파일러 플러그인 없이 전환 파일과 Kotlin 호출 경계만 delombok하고, 미전환 Java 내부는 전환 시점까지 Lombok을 유지한다. | 채택 (구현 전) |
| [`kotlin-jpa-entity-open.md`](kotlin-jpa-entity-open.md) | 기술 선택 | `kotlin-jpa` no-arg와 JPA 애노테이션 `allOpen`으로 엔티티를 열고, `@Entity`/`@MappedSuperclass`에는 `data class`를 쓰지 않는다. | 채택 (구현 전) |
| [`kotlin-data-class-scope.md`](kotlin-data-class-scope.md) | 기술 선택 | `data class`는 DTO·이벤트 페이로드·불변 값 객체에 한정하고, Java record 접근자 경계는 `@JvmRecord` 또는 같은 PR의 호출부 전환으로 맞춘다. | 채택 (구현 전) |
| [`step-up-auth.md`](step-up-auth.md) | 기능 | 크레딧 사용·계정 변경 같은 고위험 행위에 2차 비밀번호 재인증을 요구한다. | 3차 범위 |

> Kotlin 마이그레이션 결정 4건은 [Kotlin 마이그레이션 계획](../plans/kotlin-migration/00-kotlin-migration-plan.md)의 P1~P5와 연결되며, 모두 `status: 채택`·"구현 전"이다. 검증 게이트는 각 문서의 5~6번과 [코드 전환 계획](../plans/kotlin-migration/07-code-migration.md)의 `step-0`~`step-6`에 있다.

## 상태 판단 근거

| 파일 | 근거 |
|---|---|
| `access-key-policy.md` | 상태 표기 없음. 2026-09-15 갱신으로 `core-domain-decisions.md` §8 확정 내용을 반영했고, "선택 및 근거"가 확정 서술이며 검증 방법이 통합 테스트로 특정됨. |
| `authentication-policy.md` | 서두 "문서 성격: V1 적용 정책 + V2·V3 확정 방향 + 미결정 정책의 목록"(구현 기준 PR #302, dev 병합 `46526a5`). 미결정 항목(관리자 자동 잠금 예외, RT 재사용 폐기 범위, 요청 제한 수치 등)이 본문에 명시됨. |
| `authorization-policy.md` | 상태 표기 없음. 구현된 다섯 축을 표로 확정하고 실존 테스트(`AuthControllerTest#t15`)를 근거로 든다. |
| `member-withdrawal-policy.md` | 서두에 "상태: **정책 확정**" 명기 (2026-09-29). |
| `reservation-concurrency.md` | "2026-09-24 최신화 — 현재 백엔드 소스에 전면 구현되어 있다" + 단위·다중 스레드 통합 테스트 클래스명 명시. |
| `reservation-idempotency.md` | 상태 표기 없음. 2026-09-15 갱신 반영 + 실존 테스트(`ReservationPaymentConcurrencyTest`)를 검증 방법으로 제시. |
| `pg-charge.md` | 서두 "상태: 구현 중 (6단계 완료, 5단계 진행 전)". 결정(2026-10-06) 확정, 2·3·4·6단계 검증 결과 기록됨. |
| `outbox-kafka.md` | 서두 "상태: 초안 (구현 전)", "검증 결과" 절이 "(구현 후 작성)". |
| `waitlist.md` | 서두 "상태: 초안 (구현 전)", "검증 결과" 절이 "(구현 후 작성)". |
| `space-ranking.md` | 서두 "status: 구현 완료", "5. 검증 계획" 아래 "검증 결과" 기록(전체 942 tests 통과, 자기검증). 사용자 화면 확인 전. |
| `step-up-auth.md` | 서두 "**3차 프로젝트 범위**다. 2026-10-21 마감 목록에는 넣지 않는다." |

> `access-key-policy.md`, `authorization-policy.md`, `reservation-idempotency.md`는 문서 자체에 상태 표기가 없어
> 본문 근거로 "채택"으로 판단했습니다. 각 문서 서두에 상태 표기를 추가하는 것은 이 PR 범위 밖입니다.
