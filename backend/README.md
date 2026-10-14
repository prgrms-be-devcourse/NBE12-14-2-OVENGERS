# Slot Key — Backend

Spring Boot 3.3.4 / Java 21 / MySQL 8.4 / Flyway.

## 실행

로컬 실행을 위해서는 MySQL 8.4가 `localhost:3306`에 기동되어 있어야 합니다. `backend/docker-compose.yml`을 사용하여 컨테이너를 실행합니다.

### 1. 환경변수 설정 및 MySQL 컨테이너 기동
`backend/docker-compose.yml`은 `DB_PASSWORD`를 필수로 요구합니다. 예제 파일을 복사하여 `.env`를 준비하고 로컬 프로필(`application-local.yml`)의 기본 비밀번호인 `root`와 일치시킵니다. 로컬 프로필은 Spring Config Data로 같은 `.env`를 선택적으로 읽습니다.

```bash
# backend/ 디렉터리 기준 (기존 .env 파일이 없을 때만 복사)
cp -n .env.example .env

# MySQL 8.4 컨테이너 기동
docker compose up -d
```
- `.env.example`에는 로컬 개발용 `DB_PASSWORD=root`와 기본 `JWT_SECRET_KEY`가 정의되어 있습니다. 선택 환경변수는 빈 값이 기본값을 덮지 않도록 주석 처리되어 있으므로 필요할 때만 주석을 해제합니다.
- 컨테이너명: `slotkey-mysql`, 포트: `3306`, DB: `slotkey`, 계정: `root` / `root`

### 2. 백엔드 서버 기동
로컬 프로필은 `backend/.env`를 자동으로 읽습니다. 토스 승인·조회를 사용할 때만 `.env`의 `TOSS_PAYMENTS_SECRET_KEY` 주석을 해제하고 테스트 시크릿 키를 넣습니다. 키와 Authorization 헤더는 로그나 저장소에 남기지 않습니다.

```bash
./gradlew bootRun
```

`http://localhost:8080` 에서 뜹니다. Swagger UI 는 `/swagger-ui/index.html`.

| 명령 | 설명 |
| --- | --- |
| `./gradlew bootRun` | 개발 서버 (local 프로필, `backend/.env` 자동 로딩) |
| `./gradlew build` | 빌드 + 테스트 |
| `./gradlew test` | 테스트만 (Docker Testcontainers 필요) |

### 3. 로컬 실행 문제 해결

| 증상 | 원인 | 해결 |
| --- | --- | --- |
| `docker compose up -d`에서 `The container name "/slotkey-mysql" is already in use` | 이전에 만든 같은 이름의 컨테이너가 남아 있음 | 기존 컨테이너를 그대로 쓰려면 `docker start slotkey-mysql`. 비밀번호가 다르거나 마이그레이션 기록이 꼬였으면 `docker rm -f slotkey-mysql` 후 `docker compose up -d` (로컬 데이터 삭제) |
| `bootRun`에서 `Communications link failure` / `Connection refused` | MySQL 컨테이너가 떠 있지 않음 | `docker ps --filter name=slotkey-mysql`로 `Up` 상태 확인 후 다시 실행 |
| `bootRun`에서 `Migration checksum mismatch for migration version N` | 로컬 DB에 적용된 뒤 아직 push되지 않은 마이그레이션 파일이 수정됨(push 전 마이그레이션은 제자리 수정하는 규칙) | 로컬 DB를 초기화한다: `docker exec slotkey-mysql mysql -uroot -proot -e "DROP DATABASE slotkey; CREATE DATABASE slotkey;"` 후 `./gradlew bootRun`. 로컬 회원·예약 데이터는 다시 만들어야 한다 |

- 체크섬 불일치에 `flyway repair`를 쓰지 않는다. 기록된 체크섬만 바뀌고 수정된 컬럼·인덱스는 실제 테이블에 반영되지 않아, 앱은 뜨지만 해당 기능이 실행될 때 실패한다.
- 공용(배포) DB에 이미 적용된 마이그레이션은 수정하지 않고 새 번호로 추가한다. 위 초기화는 로컬 DB에만 사용한다.

## 환경변수

| 이름 | 필수 여부 | 설명 |
| --- | --- | --- |
| `JWT_SECRET_KEY` | **예** (Spring Boot 필수) | JWT 서명 키. 기본값이 없어 미설정 시 기동에 실패합니다. HS256이라 **32바이트 이상** |
| `DB_PASSWORD` | **예** (Docker Compose 필수) / 불필요 (Spring local) | `backend/docker-compose.yml` 기동 시 MySQL root 비밀번호로 필수(`backend/.env`). Spring Boot 로컬 실행 시에는 `application-local.yml`의 기본값(`root`)이 적용되므로 Spring 프로세스 환경변수로는 불필요 |
| `DB_URL` / `DB_USERNAME` | 불필요 (로컬) / **예** (배포) | 로컬에서는 `application-local.yml`이 localhost:3306/slotkey (`root`)로 덮어쓰므로 불필요. 배포 프로필에서만 필요 |
| `CORS_ALLOWED_ORIGINS` | 아니오 | 미설정 시 `http://localhost:3000` |
| `TOSS_PAYMENTS_SECRET_KEY` | PG 승인·조회 시 **예** | 토스페이먼츠 테스트 시크릿 키. 로컬 `.env` 또는 프로세스 환경변수로만 주입하며 로그에 남기지 않음 |

로컬 DB 접속 정보(`root` / `root`, 스키마 `slotkey`)는 `application-local.yml` 에 있습니다.

결제창을 포함한 로컬 성공 흐름은 [`tools/pg-sandbox`](../tools/pg-sandbox/README.md)에서 수동 검증합니다. 이 도구는 운영 코드와 `frontend/` 앱에 포함되지 않습니다.

## 알아둘 점

- **테스트는 Testcontainers 로 MySQL 을 띄웁니다.** `support/MySqlTestContainerConfig` 를
  `@Import` 해서 씁니다. 그래서 테스트를 돌리려면 Docker 가 실행 중이어야 합니다.
- **스키마 변경은 Flyway 마이그레이션으로만 합니다.** `ddl-auto: none` 이라 엔터티를 고쳐도
  테이블은 바뀌지 않습니다. `src/main/resources/db/migration` 에 다음 버전을 추가합니다.
- **프론트엔드는 `/api/v1` 로 같은 오리진에 요청합니다.** Next 의 rewrite 가 이 백엔드로
  넘기므로 평소에는 CORS 가 관여하지 않습니다.
