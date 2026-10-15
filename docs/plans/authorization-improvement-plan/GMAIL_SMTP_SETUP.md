# Gmail SMTP 이메일 인증 발송 설정

SlotKey의 이메일 인증번호 발송은 로컬과 운영 모두 Gmail SMTP를 사용한다. Mailpit 및 별도 메일 비활성·대체 발송 모드는 사용하지 않는다. 이 문서는 인증 고도화 계획 폴더 안에서 SMTP 실행·운영 절차를 한 곳에 정리한다.

## 설정 구조

공통 `backend/src/main/resources/application.yml`에서 SMTP 호스트·포트 기본값과 인증, STARTTLS, 타임아웃을 관리한다. 로컬·운영 프로필은 이를 공유하며 Gmail 선택 프로필을 추가하지 않는다.

| 환경변수 | 필수 | 역할 / 기본값 |
| --- | --- | --- |
| `SPRING_MAIL_HOST` | 선택 | SMTP 호스트. 기본 `smtp.gmail.com` |
| `SPRING_MAIL_PORT` | 선택 | SMTP 포트. 기본 `587` |
| `SPRING_MAIL_USERNAME` | 필수 | SMTP 로그인에 사용할 Gmail 주소 |
| `SPRING_MAIL_PASSWORD` | 필수 | Gmail 앱 비밀번호 |
| `VERIFICATION_MAIL_FROM` | 필수 | 발신 주소. `SPRING_MAIL_USERNAME`과 정확히 같아야 함 |
| `VERIFICATION_HASH_SECRET` | 필수 | 인증번호·증표 해싱 비밀키. 기존 설정 유지 |

SMTP 인증과 STARTTLS는 활성화되며 STARTTLS 협상이 필수다. 연결·읽기·쓰기 제한시간은 각각 5초다. 발신 주소와 로그인 주소가 다르거나 운영 SMTP 계정·비밀번호가 비어 있으면 애플리케이션 기동을 실패시켜 설정 누락을 숨기지 않는다.

Google 계정의 일반 로그인 비밀번호를 사용하지 않는다. Google 계정에 2단계 인증을 설정하고 발급한 앱 비밀번호를 `SPRING_MAIL_PASSWORD`에 등록한다. Gmail 계정 또는 앱 비밀번호는 코드·문서·저장소에 기록하지 않는다.

## 로컬 실행

저장소 루트에서 처음 한 번 예시 파일을 복사한다. 기존 `.env`가 있으면 덮어쓰지 않는다.

```bash
cd backend
cp -n .env.example .env
```

`backend/.env`에서 다음 값을 직접 채운다. 실제 비밀값은 이 문서나 채팅에 붙여 넣지 않는다.

```dotenv
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=<Gmail 주소>
SPRING_MAIL_PASSWORD=<Google 앱 비밀번호>
VERIFICATION_MAIL_FROM=<SPRING_MAIL_USERNAME과 동일한 Gmail 주소>
VERIFICATION_HASH_SECRET=<기존 비밀키>
```

발신 주소는 SMTP 로그인 주소와 동일한 문자열이어야 한다. 로컬 프로필은 `optional:file:.env[.properties]`를 사용하므로 `.env` 탐색은 **프로세스의 실행 디렉터리 기준**이다.

터미널에서는 `backend/`로 이동해 실행한다.

```bash
./gradlew bootRun
```

IntelliJ에서는 Spring Boot 실행 구성의 Working directory를 저장소의 `backend/`로 지정하고 애플리케이션을 실행한다. 실행 디렉터리가 다른 위치라면 `backend/.env`가 자동으로 로드되지 않으므로 실행 구성의 환경변수 칸에 값을 직접 지정하거나 Working directory를 바로잡는다.

MySQL은 기존처럼 `backend/docker-compose.yml`의 MySQL 서비스로 실행한다. Compose에는 Mailpit 서비스가 없다. 로컬에서 인증 발급을 요청하면 테스트 메일함이 아니라 지정한 실제 수신자에게 이메일이 발송된다.

## 운영 EC2 설정과 재시작

EC2의 `prod` 프로필은 로컬 `.env`를 읽지 않는다. 로컬 `.env`, IntelliJ 실행 구성, 개발자 컴퓨터의 환경변수는 EC2에 자동 전송되지 않는다. 운영 값은 EC2의 `slotkey.service`가 읽는 권한 제한 환경 파일 또는 서비스 환경변수에 별도로 등록한다.

기존 운영 필수 값도 함께 보존한다: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET_KEY`, `VERIFICATION_HASH_SECRET`. 여기에 아래 Gmail 값을 등록한다.

```dotenv
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=<운영 Gmail 주소>
SPRING_MAIL_PASSWORD=<운영 Gmail 앱 비밀번호>
VERIFICATION_MAIL_FROM=<SPRING_MAIL_USERNAME과 동일한 주소>
```

예를 들어 `/etc/slotkey/slotkey.env`를 사용한다면 root 소유로 만들고 애플리케이션 서비스가 읽을 수 있도록 최소 권한만 부여한다. 서비스 유닛에 `EnvironmentFile=/etc/slotkey/slotkey.env`가 실제로 연결되어 있는지 확인한다. 미연결 상태라면 서비스 override에 다음을 등록한 뒤 systemd를 다시 읽힌다.

```ini
[Service]
EnvironmentFile=/etc/slotkey/slotkey.env
```

```bash
sudo systemctl daemon-reload
sudo systemctl restart slotkey
sudo systemctl is-active slotkey
sudo journalctl -u slotkey -n 100 --no-pager
```

GitHub Actions 배포는 새 JAR를 배치하고 `slotkey` 서비스를 재시작한다. 배포 자체가 로컬 `.env`를 복사하거나 Gmail 자격 증명을 전달하지는 않는다. EC2 환경 파일과 systemd 연결은 운영자가 별도로 설정·보호해야 하며, SMTP 필수값이 빠지면 서비스 기동이 실패한다.

## 자동 테스트와 사람이 수행할 검증

자동 테스트에서는 인증 흐름의 `VerificationMailSender`를 Mock 처리하고, 메일 내용 구성 테스트는 `JavaMailSender`를 Mock한다. `test` 프로필에는 Gmail 자격 증명이 필요하지 않으며, `spring.mail.test-connection`은 설정하지 않아 기동 시 SMTP 접속을 시도하지 않는다. 메일 제목·수신자·본문은 SMTP 네트워크 없이 검증하고, 기존 발송 실패 시 트랜잭션 롤백 테스트도 유지한다.

실제 수신 확인과 EC2 검증은 자동 테스트가 대신하지 않는다. 다음 절차는 운영자가 수행한다.

1. 실제 접근 가능한 수신자 이메일로 인증번호 발급을 요청하고, 받은 편지함/스팸함에서 수신과 발신자·제목·본문을 확인한다.
2. 인증번호를 확인 API에 제출하고 회원가입이 성공하는지 확인한다.
3. 비밀번호 재설정 인증 메일을 수신하고 재설정을 완료한 다음 새 비밀번호로 로그인한다.
4. 배포 후 EC2에서도 인증번호 수신, 인증 확인, 가입, 비밀번호 재설정 및 새 비밀번호 로그인을 반복 확인한다.
