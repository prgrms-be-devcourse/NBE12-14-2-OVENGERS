# Slot Key 문서 색인

목적별로 묶은 문서 지도입니다. 설계 판단이 서로 어긋날 때는
[`core-domain-decisions.md`](core-domain-decisions.md)를 기준으로 삼습니다.

| 폴더 | 무엇이 있나 | 대표 문서 |
|---|---|---|
| [`product/`](product/) | 기획·요구사항·약관·UI 결정 | [요구사항 정의](product/requirements.md) · [UI 의사결정](product/UI_DESIGN_DECISIONS.md) · [예약 약관 문안 검토](product/booking-terms-review.md) |
| [`architecture/`](architecture/) | 시스템 구조와 데이터 모델 | [시스템 아키텍처](architecture/system-architecture.md) · [ERD](architecture/erd.md) |
| [`api/`](api/) | API 명세와 연동 계약 | [REST API 명세](api/api-spec.md) · [프론트 결제 API 계약](api/FRONTEND2_PAYMENT_API.md) · [관리자 공간 대표 사진](api/admin-space-image-upload.md) |
| [`decisions/`](decisions/) | 기능별 의사결정 기록 | [결정 상태표](decisions/README.md) |
| [`operations/`](operations/) | 배포와 장애 대응 | [배포·CI/CD](operations/deploy-cicd.md) · [트러블슈팅](operations/troubleshooting.md) |
| [`testing/`](testing/) | 테스트 전략과 실행 결과 | [테스트 전략](testing/test-strategy.md) · [테스트 결과](testing/test-results.md) |
| [`plans/`](plans/) | 진행 예정 계획 시리즈 | [Kotlin 마이그레이션 준비](plans/kotlin-migration/00-kotlin-migration-plan.md) · [인증·인가 고도화](plans/authorization-improvement-plan/03-auth-improvement-roadmap.md) |

## 루트에 두는 문서

- [`core-domain-decisions.md`](core-domain-decisions.md) — 예약·체크인 핵심 비즈니스 로직 확정본.
  다른 문서와 충돌하면 이 문서가 우선합니다. `AGENTS.md`와 백엔드·프론트엔드 소스 주석이
  이 경로를 직접 참조하므로 루트에 둡니다.

## 계획 시리즈

진행 예정 작업은 [`plans/`](plans/) 아래에 시리즈로 둡니다.

- **Kotlin 마이그레이션 준비** — [준비 계획](plans/kotlin-migration/00-kotlin-migration-plan.md) 외 7편.
  이 저장소에서는 Kotlin으로 옮기지 않고, 3차 프로젝트에서 새 저장소에 적용합니다.
- **인증·인가 고도화** — [Redis 도입 범위와 배경](plans/authorization-improvement-plan/01-redis-adoption-scope.md) · [Redis 인증·인가 설계 초안](plans/authorization-improvement-plan/02-redis-auth-design.md) · [단계별 계획](plans/authorization-improvement-plan/03-auth-improvement-roadmap.md)
