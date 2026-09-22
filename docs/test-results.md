# 테스트 결과

> 이 문서는 마지막으로 로컬에서 `./gradlew test`를 돌린 결과를 정리한 것이다. CI(`backend-ci.yml`)는 PR마다 전체 테스트를 실행하지만 결과를 이 문서에 자동 반영하지 않으므로, 최신 상태를 보려면 GitHub Actions 실행 로그를 직접 확인한다. 아래 수치는 2026-09-22 기준 로컬 실행 결과(`backend/build/test-results/test/*.xml`, 실행 시각 2026-09-21 03:29 UTC)를 집계한 것이며, 그 이후 추가된 테스트 클래스는 반영되어 있지 않다(아래 "최근 실행에 없는 테스트 클래스" 참고).

## 요약

- 테스트 클래스(소스 기준): 55개, `@Test` 메서드 298개
- 마지막 로컬 실행: 42개 클래스, 258개 테스트, **실패 0 / 에러 0 / 스킵 0**
- 도구: JUnit 5, Mockito, MockMvc, Testcontainers(MySQL 8.4), `Clock` 주입(경계 테스트), `ExecutorService`/`CountDownLatch`(동시성 테스트) — 자세한 도구 선택 이유는 `docs/test-strategy.md` 참고.

## 도메인별 테스트 수 (마지막 로컬 실행 기준)

| 도메인 | 테스트 수 | 비고 |
| --- | --- | --- |
| reservation | 98 | HOLD·결제·연장·취소·체크아웃·스케줄러·동시성(슬롯 충돌, 인접 슬롯, 부분 겹침, 결제 경합) |
| space | 75 | CRUD, 운영시간 정책, 슬롯 가용성, 관리자 보안 필터 체인 |
| access | 27 | 출입 토큰 발급/폐기/검증, 시간 경계, 잠금 기반 재검증(#205) |
| auth | 19 | 로그인/로그아웃/리프레시, 정지 계정 처리 |
| credit | 19 | 크레딧 지급, 차감/환급 |
| member | 10 | 관리자 회원 관리 |
| audit | 7 | AuditLog 트랜잭션 원자성 (감사 로그 목록 조회 API 테스트는 최근 실행에 없음 — 아래 참고) |
| global | 3 | CORS, JPA Auditing, JwtProvider |

## 최근 실행에 없는 테스트 클래스 (소스에는 있으나 마지막 로컬 실행 이후 추가됨)

다음 13개 클래스는 소스에 존재하지만 위 집계에 포함되지 않았다. 대부분 9/21~9/22에 추가된 동시성·감사 로그·결제 관련 테스트다. 다음 `./gradlew test` 실행 시 이 문서의 수치를 함께 갱신한다.

- `audit.controller.AdminAuditLogControllerTest`, `audit.controller.AdminAuditLogSecurityIntegrationTest`, `audit.repository.AuditLogRepositoryTest`, `audit.service.AuditLogQueryServiceTest` — 관리자 감사 로그 목록 조회 API(#213)
- `auth.service.AuthServiceTest`
- `member.authorization.MemberAuthorizationServiceTest`, `member.service.AdminMemberServiceIntegrationTest`
- `payment.gateway.MockPaymentGatewayTest`, `payment.service.PaymentServiceTest` — `payment` 패키지는 `core-domain-decisions.md`(2026-09-15)에서 `credit_transaction`으로 단일화하기로 한 개념과 이름이 겹친다. 남아있는 이유(사용 중인지, 정리 대상인지)를 팀에서 한 번 확인할 필요가 있다.
- `reservation.service.AdminReservationConcurrencyIntegrationTest`, `reservation.service.AdminReservationServiceIntegrationTest`, `reservation.service.ReservationAdjacentSlotConcurrencyTest`, `reservation.service.ReservationPartialOverlapConcurrencyTest`

## 알려진 제약

- Testcontainers를 쓰는 통합 테스트는 Docker가 필요하다. Docker가 없는 환경에서는 해당 테스트가 실패하거나 스킵될 수 있다.
- 위 수치는 로컬 1회 실행 스냅샷이다. CI 결과가 최종 기준이며, 다를 경우 CI 쪽을 신뢰한다.
