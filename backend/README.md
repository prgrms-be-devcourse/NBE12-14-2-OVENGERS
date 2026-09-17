# Slot Key — Backend

Spring Boot 3.3 / Java 21 / MySQL 8 / Flyway.

## 실행

MySQL 8 이 `localhost:3306` 에 떠 있어야 합니다. 없다면 컨테이너로 띄웁니다.

```bash
docker run -d --name slotkey-mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=slotkey mysql:8.4
```

그다음 백엔드를 실행합니다. 스키마는 Flyway 가 첫 기동 때 만듭니다.

```bash
JWT_SECRET_KEY='local-dev-only-secret-key-must-be-32bytes-or-longer' ./gradlew bootRun
```

`http://localhost:8080` 에서 뜹니다. Swagger UI 는 `/swagger-ui/index.html`.

| 명령 | 설명 |
| --- | --- |
| `./gradlew bootRun` | 개발 서버 (local 프로필) |
| `./gradlew build` | 빌드 + 테스트 |
| `./gradlew test` | 테스트만 |

## 환경변수

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| `JWT_SECRET_KEY` | **예** | JWT 서명 키. 기본값이 없어 없으면 기동에 실패합니다. HS256 이라 **32바이트 이상** |
| `CORS_ALLOWED_ORIGINS` | 아니오 | 미설정 시 `http://localhost:3000` |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | 아니오 | local 프로필에서는 `application-local.yml` 이 덮으므로 불필요. 배포에서만 필요 |

로컬 DB 접속 정보(`root` / `root`, 스키마 `slotkey`)는 `application-local.yml` 에 있습니다.

## 알아둘 점

- **테스트는 Testcontainers 로 MySQL 을 띄웁니다.** `support/MySqlTestContainerConfig` 를
  `@Import` 해서 씁니다. 그래서 테스트를 돌리려면 Docker 가 실행 중이어야 합니다.
- **스키마 변경은 Flyway 마이그레이션으로만 합니다.** `ddl-auto: none` 이라 엔터티를 고쳐도
  테이블은 바뀌지 않습니다. `src/main/resources/db/migration` 에 다음 버전을 추가합니다.
- **프론트엔드는 `/api/v1` 로 같은 오리진에 요청합니다.** Next 의 rewrite 가 이 백엔드로
  넘기므로 평소에는 CORS 가 관여하지 않습니다.
