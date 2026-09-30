# 배포 및 CI/CD

> 2026-09-25 기준. 3차(코틀린 마이그레이션) 프로젝트에서는 이 문서에 정리된 배포·CI/CD 구조를 그대로 유지하고, PG 연동·예약 대기열은 다음 사이클로 미룸.

## 1. 인프라 구성

| 구성 요소 | 내용 |
| --- | --- |
| 백엔드 | AWS EC2 (Ubuntu), Spring Boot 3.3.4 + Java 21, Gradle |
| DB | AWS RDS MySQL 8.4 |
| 프론트엔드 | Next.js(App Router), `STATIC_EXPORT=true npm run build`로 정적 export 후 S3 + CloudFront 배포 |
| S3 버킷 | `slotkey-web-page` |
| CloudFront distribution ID | `E3PEHWSJNM0BPV` |
| AWS 인증 | IAM 사용자 `slotkey-deploy` (CLI 전용, S3FullAccess + CloudFrontFullAccess). 루트 계정 액세스 키는 사용하지 않음 |

## 2. 백엔드 배포 방식

- **jar 직접 실행 방식** 채택 (Docker 미사용 — 팀의 docker-compose는 로컬 DB 전용). systemd로 애플리케이션 자체를 관리하지 않고 `nohup`으로 백그라운드 실행.
- 저장소 경로: `/home/ubuntu/NBE12-14-2-OVENGERS/backend`
- 실행 jar: `build/libs/slotkey-0.0.1-SNAPSHOT.jar`
- 실행 포트: `8080` (커스텀 `server.port` 설정 없음, 기본값)
- 실행 프로필: `--spring.profiles.active=prod`
- **알려진 제약**: EC2가 저사양이라 `./gradlew build` 중 테스트 태스크가 OOM(exit 137)으로 killed되는 문제가 있었음 → EC2에서 직접 빌드할 때는 `./gradlew build -x test`로 테스트 스킵.
- **Actuator 미적용**: 헬스체크용 `/actuator/health` 엔드포인트가 없음. 배포 파이프라인의 헬스체크는 HTTP 응답 코드가 오는지(`000`이 아닌지)만 확인하는 방식으로 대체.

## 3. CI/CD (GitHub Actions)

파일 위치: `.github/workflows/backend-ci.yml` (기존 PR 빌드 워크플로에 배포 job을 추가하는 방식으로 확장 — 별도 워크플로 파일을 새로 만들지 않음).

### 3-1. 트리거

```yaml
on:
  pull_request:
    branches: [dev, main]
  push:
    branches: [dev]
```

- PR 생성/업데이트 시: `build` job만 실행 (테스트 포함 빌드 검증).
- `dev` 브랜치에 실제 push(merge)될 때: `build` → `deploy` 순서로 실행.
- **참고**: 현재는 `dev` push만 실제 배포를 트리거함. 추후 `main`으로 배포 대상을 옮길 경우 `deploy` job의 `if` 조건과 `push.branches`를 같이 수정해야 함.

### 3-2. build job

- `runs-on: ubuntu-latest` (GitHub 호스팅 러너)
- JDK 21(Temurin) 설정 → `./gradlew build` (테스트 포함) 실행
- 저사양 EC2에서 겪었던 OOM 문제와 무관 — GitHub 호스팅 러너는 별도 리소스라 테스트를 스킵하지 않고 그대로 실행

### 3-3. deploy job

- `runs-on: self-hosted` — EC2 인스턴스 자체에 등록한 GitHub Actions self-hosted runner에서 실행
- `needs: build` — build job 성공 시에만 실행
- 조건: `dev` 브랜치로의 push 이벤트일 때만 실행 (PR에서는 스킵)
- 단계: 체크아웃 → JDK 21 설정 → `./gradlew build -x test`(빌드 job에서 이미 테스트 통과했으므로 재실행 스킵) → 기존 프로세스 종료(`pkill -f 'slotkey-0.0.1-SNAPSHOT.jar'`) → `nohup java -jar ... &`로 재기동 → HTTP 응답 코드 기반 헬스체크(최대 10회, 5초 간격 재시도)

### 3-4. self-hosted runner를 선택한 이유

GitHub Actions에서 EC2로 배포하는 방법은 크게 두 가지:

1. **SSH 직접 접속**: GitHub Secrets에 SSH 키를 등록하고 Actions가 EC2에 접속. EC2의 22번 포트를 인터넷에 열어야 함 — GitHub 호스팅 러너는 고정 IP가 아니라서 IP 기준으로 좁힐 수 없고, 사실상 전체 개방(0.0.0.0/0)이 되어 무차별 대입 공격 등 보안 리스크가 커짐.
2. **self-hosted runner (채택)**: EC2 자체를 GitHub Actions 러너로 등록. 러너가 EC2 안에서 GitHub 쪽으로 나가는 방향으로만 통신하므로, 22번 포트를 열 필요가 전혀 없음. EC2 재시작 시 퍼블릭 IP가 바뀌어도(Elastic IP 미사용) 워크플로 설정을 손볼 필요가 없다는 부수적 이점도 있음.

보안과 운영 안정성 면에서 self-hosted runner가 낫다고 판단해 이 방식을 채택함.

### 3-5. self-hosted runner 설치 정보

- 설치 경로: `/home/ubuntu/actions-runner`
- systemd 서비스로 등록 (`sudo ./svc.sh install && sudo ./svc.sh start`) → EC2 재부팅 시에도 자동 기동
- 서비스명 예: `actions.runner.prgrms-be-devcourse-NBE12-14-2-OVENGERS.ip-172-31-46-159.service`
- 등록 확인: 저장소 Settings → Actions → Runners에서 상태 확인 가능

### 3-6. 프론트엔드 배포 자동화 (2026-09-30 추가)

파일 위치: `.github/workflows/frontend-ci.yml`

- 이전에는 `STATIC_EXPORT=true npm run build` → S3 업로드 → CloudFront invalidation을 매번 콘솔/CLI에서 수동으로 했음. 이제 `dev` push 시 자동으로 실행됨.
- 트리거는 백엔드와 동일하게 `pull_request: [dev, main]`(빌드 검증만) / `push: [dev]`(빌드+배포), 다만 `paths: frontend/**`로 제한해 백엔드만 바뀐 PR·push에서는 실행되지 않음.
- `build` job (`ubuntu-latest`): `npm ci` → `npm run lint` → `tsc --noEmit` → `node --test tests/*.cjs` → `STATIC_EXPORT=true npm run build`(실제 배포 빌드와 동일한 조건으로 빌드 자체가 되는지 검증).
- `deploy` job: `build` 성공 + `dev` push일 때만, `ubuntu-latest`에서 실행(백엔드와 달리 self-hosted runner 불필요 — EC2에 접속할 필요 없이 AWS API만 호출하면 되므로).
  1. `STATIC_EXPORT=true npm run build`로 `frontend/out/` 생성
  2. `aws-actions/configure-aws-credentials`로 IAM 사용자 `slotkey-deploy`의 키를 사용해 인증
  3. `aws s3 sync out/ s3://slotkey-web-page --delete`
  4. `aws cloudfront create-invalidation --distribution-id E3PEHWSJNM0BPV --paths "/*"`
- **필요한 GitHub Secrets** (저장소 Settings → Secrets and variables → Actions에 등록 필요, 아직 미등록 상태):
  - `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`: IAM 사용자 `slotkey-deploy`의 CLI 키
  - `AWS_REGION`: S3 버킷 리전(예: `ap-northeast-2`)
  - 시크릿이 없으면 `deploy` job의 `Configure AWS credentials` 단계에서 실패함.

## 4. 검증 기록 (2026-09-25)

- `dev` push → `build`(3m35s) → `deploy`(1m52s), 총 5m33s 만에 자동 배포 완료 확인
- 배포 후 `ps aux`로 프로세스 PID가 재기동 시점 기준으로 새로 바뀐 것 확인
- 외부에서 `curl http://<EC2 퍼블릭 IP>:8080` 호출 시 정상 응답(`AUTHENTICATION_REQUIRED`) 확인 — 서버 및 인증 필터 정상 동작

## 5. 향후 개선 후보 (이번 사이클에서는 보류)

- Elastic IP 미적용 상태 — EC2 재시작 시 IP가 바뀔 수 있음(현재 self-hosted runner 구조라 CI/CD 자체는 영향 없지만, 프론트/문서에 박아둔 IP 값은 갱신 필요)
- Actuator 도입 후 정식 `/actuator/health` 기반 헬스체크로 전환
- `main` 브랜치 배포 전환 시 워크플로 조건 수정
- PG 연동, 예약 대기열은 별도 사이클에서 진행 예정