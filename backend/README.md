# Slot Key — Backend

Spring Boot 3.3.4 / Java 21 / MySQL 8.4 / Flyway.

## 실행

로컬 실행을 위해서는 MySQL 8.4가 `localhost:3306`에 기동되어 있어야 합니다. `backend/docker-compose.yml`을 사용하여 컨테이너를 실행합니다.

### 1. 환경변수 설정 및 MySQL 컨테이너 기동
`backend/docker-compose.yml`은 `DB_PASSWORD`를 필수로 요구합니다. 예제 파일을 복사하여 `.env`를 준비하고 로컬 프로필(`application-local.yml`)의 기본 비밀번호인 `root`와 일치시킵니다.

```bash
# backend/ 디렉터리 기준 (기존 .env 파일이 없을 때만 복사)
cp -n .env.example .env

# MySQL 8.4 컨테이너 기동
docker compose up -d
```
- `.env.example`에는 로컬 개발용 `DB_PASSWORD=root`와 기본 `JWT_SECRET_KEY`가 이미 정의되어 있어, 파일 복사 후 별도의 중복 추가 없이 바로 기동할 수 있습니다.
- 컨테이너명: `slotkey-mysql`, 포트: `3306`, DB: `slotkey`, 계정: `root` / `root`

### 2. 백엔드 서버 기동
`build.gradle`에는 `.env` 자동 로딩 설정이 없으므로, 프로세스 기동 시 필수 환경변수인 `JWT_SECRET_KEY`를 환경변수로 직접 주입하여 실행합니다 (스키마는 Flyway가 첫 기동 때 V1~V10을 자동 생성합니다).

```bash
JWT_SECRET_KEY='local-dev-only-secret-key-must-be-32bytes-or-longer' ./gradlew bootRun
```

`http://localhost:8080` 에서 뜹니다. Swagger UI 는 `/swagger-ui/index.html`.

| 명령 | 설명 |
| --- | --- |
| `JWT_SECRET_KEY=... ./gradlew bootRun` | 개발 서버 (local 프로필) |
| `./gradlew build` | 빌드 + 테스트 |
| `./gradlew test` | 테스트만 (Docker Testcontainers 필요) |

## 환경변수

| 이름 | 필수 여부 | 설명 |
| --- | --- | --- |
| `JWT_SECRET_KEY` | **예** (Spring Boot 필수) | JWT 서명 키. 기본값이 없어 미설정 시 기동에 실패합니다. HS256이라 **32바이트 이상** |
| `DB_PASSWORD` | **예** (Docker Compose 필수) / 불필요 (Spring local) | `backend/docker-compose.yml` 기동 시 MySQL root 비밀번호로 필수(`backend/.env`). Spring Boot 로컬 실행 시에는 `application-local.yml`의 기본값(`root`)이 적용되므로 Spring 프로세스 환경변수로는 불필요 |
| `DB_URL` / `DB_USERNAME` | 불필요 (로컬) / **예** (배포) | 로컬에서는 `application-local.yml`이 localhost:3306/slotkey (`root`)로 덮어쓰므로 불필요. 배포 프로필에서만 필요 |
| `CORS_ALLOWED_ORIGINS` | 아니오 | 미설정 시 `http://localhost:3000` |

로컬 DB 접속 정보(`root` / `root`, 스키마 `slotkey`)는 `application-local.yml` 에 있습니다.

## 알아둘 점

- **테스트는 Testcontainers 로 MySQL 을 띄웁니다.** `support/MySqlTestContainerConfig` 를
  `@Import` 해서 씁니다. 그래서 테스트를 돌리려면 Docker 가 실행 중이어야 합니다.
- **스키마 변경은 Flyway 마이그레이션으로만 합니다.** `ddl-auto: none` 이라 엔터티를 고쳐도
  테이블은 바뀌지 않습니다. `src/main/resources/db/migration` 에 다음 버전을 추가합니다.
- **프론트엔드는 `/api/v1` 로 같은 오리진에 요청합니다.** Next 의 rewrite 가 이 백엔드로
  넘기므로 평소에는 CORS 가 관여하지 않습니다.
