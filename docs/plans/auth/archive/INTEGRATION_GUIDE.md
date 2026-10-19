# 인증 문서 묶음 반영 안내

> **보관 문서 — 과거 반영 안내이며 현재 작업 지침이 아님**
> 2026-10-15에 인증 문서 묶음을 저장소에 넣기 위해 작성된 안내다. 2026-10-18 문서 정리에서 아래 "반영 현황"대로 처리했으며, 남은 항목도 이 문서가 아니라 최신 사용자 지시·현재 코드·[인증 문서 지도](../README.md)를 기준으로 진행한다.
> 본문의 경로는 작성 당시 `docs/` 기준 표기다. §3 표의 링크만 이 보관 위치에서 열리도록 상대 경로를 고쳤고, 나머지 본문은 원문 그대로 둔다.

## 반영 현황 (2026-10-18 정리 기준)

| 안내 | 상태 | 비고 |
| --- | --- | --- |
| §1 문서 7개 추가 | 반영 | `docs/plans/auth/`(README·roadmap·v2-plan·v3-redis-design), `docs/decisions/authentication-policy.md`, `docs/architecture/auth-v1-implementation.md`, `docs/testing/auth-security-tests.md` |
| §2 상대 링크·코드 경로·기준 커밋 확인 | 반영 | 링크 검증. PR #302는 이후 dev에 `46526a5`로 병합됨을 각 문서에 덧붙임 |
| §3 docs/README 문서 지도 | 반영(형태 변경) | 질문표 대신 인증 고도화 시작점을 `plans/auth/README.md`로 변경. 질문별 안내는 인증 문서 지도에 있음 |
| §4 decisions/README 상태표 | 반영 | 상태 표현 "V1 적용 / V2·V3 방향 포함 / 세부 미결정 항목 존재" 사용 |
| §5 기존 Redis 문서 정리 | 반영(방식 변경) | "통합·대체" 후 삭제 대신 `archive/redis-initial-plan/`에 원문 보존하고 보관 표시 추가. 현재 기준은 roadmap·v3-redis-design |
| §5 기존 경로 안내문 | 미수행 | 저장소 안 참조는 모두 새 경로로 수정. 외부 공유 여부는 확인되지 않아 안내문을 남기지 않음 |
| §6 공통 API 명세 보완 | 반영(2차 정리) | `api/api-spec.md` §2에 현재 코드 기준으로 반영. 회원탈퇴(`POST /auth/withdraw`)는 아직 명세에 없음 |
| §7 ERD·아키텍처 보완 | 반영(2차 정리) | `architecture/erd.md`에 V14–V16 반영. 미래 AuthSession은 넣지 않음 |
| §8 test-results.md 갱신 | 반영(2차 정리) | PR head `5c60fa4`의 과거 CI 기록으로 §0에 추가하고 현재 `@Disabled` 설정과 구분. 현재 HEAD 결과는 미확인으로 표시 |
| §9 운영 보완 | 일부 반영 | SMTP는 [`operations/gmail-smtp-setup.md`](../../../operations/gmail-smtp-setup.md)를 단일 기준으로 둠(로컬·운영 Gmail SMTP, Mailpit 없음). troubleshooting 항목은 미수행 |

---

작성일: 2026-10-15. 이 묶음은 사용자가 제안한 docs 분류 구조에 추가할 인증 문서 7개다. 기존 프로젝트 문서 전체를 대체하지 않는다.

## 1. 포함 파일

| 경로 | 용도 |
| --- | --- |
| docs/plans/auth/README.md | 인증 문서 지도와 기준 근거 |
| docs/plans/auth/roadmap.md | V1–V3 상태와 개발 순서 |
| docs/decisions/authentication-policy.md | 현재 정책·목표 정책·미결정 항목 |
| docs/architecture/auth-v1-implementation.md | 코드 흐름과 설계·트랜잭션 해설 |
| docs/testing/auth-security-tests.md | 11개 레드 테스트의 의미·증거·한계 |
| docs/plans/auth/v2-plan.md | DB 구현 순서·결정표·검증 |
| docs/plans/auth/v3-redis-design.md | Redis 이전·원자성·장애·성능 비교 초안 |

## 2. 반영 방법

1. 기존 docs 파일 이동과 기존 링크 수정을 먼저 진행한다.
2. 이 묶음의 docs 하위 파일을 같은 경로로 추가한다.
3. 아래 안내에 따라 공통 문서의 해당 부분만 보완한다.
4. 상대 링크, 코드 경로, 기준 커밋과 CI 결과를 확인한다.
5. 문서 변경만으로 구현·병합·배포가 완료된 것처럼 표시하지 않는다.

기존 원본 문서는 이 묶음에 포함하지 않았다. 실제 저장소의 최신 내용을 유지하며 필요한 절만 병합한다. 자동 덮어쓰기용 스크립트는 제공하지 않는다.

## 3. docs/README.md에 추가할 문서 지도

아래 표의 경로는 docs/README.md 기준이다.

| 질문 | 문서 |
| --- | --- |
| 인증 고도화 작업을 처음 읽는가? | [인증 문서 지도](../README.md) |
| 인증번호·가입·복구 정책은? | [인증 정책](../../../decisions/authentication-policy.md) |
| 인증 코드 흐름과 설계 이유는? | [V1 구현 해설](../../../architecture/auth-v1-implementation.md) |
| 의도적으로 실패하는 11개 테스트는? | [보안 테스트](../../../testing/auth-security-tests.md) |
| 다음 단계에서 무엇을 구현하는가? | [로드맵](../roadmap.md) |

## 4. decisions/README.md의 상태표

authentication-policy.md는 하나의 결정만 담는 엄격한 ADR 형식보다는 정책 모음이다. 기존 decisions 폴더를 정책·설계 결정 모음으로 사용한다면 그대로 추가한다. 향후 특정 기술 선택의 대안과 채택 이유가 확정되면 별도 ADR로 분리할 수 있다.

추천 상태 표현: “V1 적용 / V2·V3 방향 포함 / 세부 미결정 항목 존재”. 단순 “완료”는 사용하지 않는다.

## 5. 기존 Redis 계획 문서 정리

| 기존 | 처리 |
| --- | --- |
| authorization-improvement-plan/01-redis-adoption-scope.md | V3 설계의 범위·책임으로 통합 |
| authorization-improvement-plan/02-redis-auth-design.md | V3 설계 초안으로 통합 |
| authorization-improvement-plan/03-auth-improvement-roadmap.md | plans/auth/roadmap.md로 대체 |

외부 링크가 이미 공유됐다면 기존 경로에 새 문서 링크만 둔 안내문을 잠시 남기는 방법을 검토한다. 오래된 설계와 새 설계를 동시에 현재 기준으로 유지하지 않는다.

## 6. 공통 API 명세 보완 항목

다음 실제 경로를 기준으로 요청 DTO·응답·오류 코드를 확인하여 기존 api-spec에 반영한다. 이 안내는 완전한 API 스펙을 대신하지 않는다.

| API | 추가·갱신 사항 |
| --- | --- |
| POST /api/v1/auth/verification/issue | email·purpose, challengeId·expiresAt·resendAvailableAt, 차단·재발송·메일 오류 |
| POST /api/v1/auth/verification/check | email·purpose·challengeId·code, grant·expiresAt, 실패·차단 오류, no-store |
| POST /api/v1/auth/signup | passwordConfirm·grant 필수, 목적·귀속 검증, 가입 후 별도 로그인 |
| POST /api/v1/auth/password-reset | email·grant·password·passwordConfirm, V1 기존 토큰 유지 한계 |
| POST /api/v1/auth/refresh | V1은 새 AT만 반환, V2의 RT 쿠키 교체는 계획 |
| POST /api/v1/auth/logout | V1 RT 폐기와 V2 세션 폐기 목표 구분 |

HTTP 오류 코드는 ErrorCode와 실제 Controller·ExceptionHandler를 기준으로 확인한다. 목표 테스트의 401을 현재 모든 계정 상태 오류의 응답 코드로 복사하지 않는다.

## 7. ERD·아키텍처 보완 항목

- LocalCredential과 Member 관계 및 비밀번호 저장 책임 변경.
- Member.emailVerifiedAt 및 기존 회원 인정 정책.
- VerificationGuard의 email·purpose 유일 제약.
- Guard–Challenge 다대일, Challenge–Grant 일대일 관계.
- Grant의 challenge와 해시 유일 제약.
- V14–V16 migration 순서.
- 현재는 DB 기반이며 Redis·AuthSession은 미구현임을 표시.

ERD에 미래 AuthSession을 넣을 경우 현재 스키마와 분리하여 제안 모델임을 명시한다.

## 8. testing/test-results.md 갱신용 기록

기준 커밋: 5c60fa444dfc427016954fdacc44d3dfd293c004  
CI: https://github.com/prgrms-be-devcourse/NBE12-14-2-OVENGERS/actions/runs/38684148319  
결과: 694개 중 682개 통과, 11개 실패, 1개 건너뜀. 실패는 V2SecurityRedTest의 의도적인 보안 단언이다.

예전 실행 결과는 역사 기록으로 보존하되 최신 결과와 구분한다. 현재 전체 테스트가 성공했다는 제목을 사용하지 않는다. 테스트 의미는 auth-security-tests.md로 연결한다.

## 9. 운영·트러블슈팅 보완 항목

- Java 21, Testcontainers Docker 조건, 테스트 메일 서버 준비.
- 운영 SMTP 제공자·발신 주소·도메인·실패 재시도·관측의 미완료 상태.
- migration 재번호가 이미 실행된 환경에 미치는 영향 확인.
- 인증번호 1차 차단에서 잘못 정지된 결함: 반환값 의미 오해와 단계별 회귀 테스트.
- grant 소비와 bulk update flush·clear: 현재 구현 순서 설명.
- EmailSignupIntegrationTest 과거 실패는 관측 증상과 현재 코드 근거를 분리. 재현 없는 원인 확정 금지.

## 10. 검토 기준과 제한

문서는 사용자 제공 V1 정책·구현·V2 계획, 인계서, PR 소스와 검토 CI를 바탕으로 작성했다. 충돌 시 최신 사용자 합의와 확인된 구현을 구분하여 반영했다.

이번 산출물은 코드·커밋·원격 PR·배포를 변경하지 않는다. V2·V3 데이터 모델과 Redis 키는 설계 후보이며 수치·성능 결과를 만들어 넣지 않았다.
