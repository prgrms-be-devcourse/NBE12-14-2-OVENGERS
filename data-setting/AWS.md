# EC2 + RDS 샘플 추가·수정

`aws-setup.sh`는 EC2에서 MySQL CLI로 RDS에 연결합니다. Docker는 사용하지 않습니다.
기존 `setup.sh`는 로컬 Docker 전용으로 유지합니다.

## 명령과 변경 범위

| 명령 | 동작 |
| --- | --- |
| `setup` | 없는 AWS 회원·관리자·공간 추가, 등록된 공간 이름·이미지 경로 갱신 |
| `update` | 등록된 AWS 공간 이름·이미지 경로 갱신. 없는 회원/공간은 추가하지 않음 |
| `status` | AWS 등록 데이터와 지역별 공간 수 조회. DB/파일 변경 없음 |
| `images` | EC2 이미지 39장 설치·확인. DB 연결 없음 |

- 일반 회원: `aws001@amazon.com` ~ `aws100@amazon.com`, USER/ACTIVE, 초기 100,000 크레딧 및 동일 금액 SIGNUP_GRANT 원장.
- 관리자: `admin000@amazon.com`, ADMIN/ACTIVE, 초기 잔액 0.
- 판교·하남·강남 각 13개 공간, 08:00~23:00, 30분당 2,000~6,500원, 수용인원 2~12명.
- 새 계정의 공통 비밀번호는 실행 시 직접 정합니다. 기존 계정의 비밀번호·역할·상태·닉네임·잔액·원장·예약은 바뀌지 않습니다.
- 기존 공간의 가격·수용인원·영업시간·상태·가격 version은 보존합니다. 표시 수정은 가격 version을 증가시키지 않습니다.
- 공간 이름·이미지 매핑의 기준은 `sql/aws_setup.sql`입니다. 기준을 수정한 뒤 `update`로 적용합니다. 가격/잔액 등의 수정은 관리자 기능을 사용하세요.
- `update`는 관리자 화면에서 바꾼 샘플 공간 이름/사진도 SQL 기준으로 되돌립니다. 예전 업로드 파일의 자동 정리나 감사 로그 생성은 하지 않습니다.
- AWS 관리자 키는 `aws-admin-000`입니다. 이전 SQL로 만든 `admin-000` 키는 연결된 이메일이 AWS 관리자와 일치할 때만 이동합니다. 로컬 샘플은 유지합니다.
- 등록되지 않은 이메일/신규 공간명 충돌, 등록 대상 행 누락, 등록 회원 이메일 변경은 오류로 중단합니다. DB 변경은 롤백합니다.
- 삭제·reset 명령은 제공하지 않습니다. `clean.sql`을 RDS에 직접 실행하지 마세요. 해당 SQL은 AWS/로컬 등록 범위를 구분하지 않습니다.

## EC2에서 최초 실행

프로젝트 루트 또는 Actions checkout의 프로젝트 루트에서 실행합니다. 이 스크립트/원본 이미지가 EC2에 있어야 합니다.
백엔드를 한 번 정상 실행해 Flyway V12 이상을 적용하고, 이미지 저장 디렉터리를 준비하세요.

```bash
sudo apt update
sudo apt install -y mysql-client python3-bcrypt

# 현재 서비스가 ubuntu로 실행되는 경우. 실제 서비스 사용자에 맞춥니다.
sudo install -d -o ubuntu -g ubuntu -m 750 /var/slotkey/space-images

# 처음 한 번만 설정 디렉터리/인증서를 준비합니다.
install -d -m 700 "$HOME/.config/slotkey-samples"
curl --fail --show-error --location \
  https://truststore.pki.rds.amazonaws.com/ap-northeast-2/ap-northeast-2-bundle.pem \
  -o "$HOME/.config/slotkey-samples/rds-ca.pem"

# 파일이 없다면 새로 만들고, 기존 파일이 있다면 편집합니다.
touch "$HOME/.config/slotkey-samples/mysql.cnf"
chmod 600 "$HOME/.config/slotkey-samples/mysql.cnf"
nano "$HOME/.config/slotkey-samples/mysql.cnf"
```

옵션 파일에 다음 내용을 넣습니다. 경로는 `$HOME` 대신 **실제 절대 경로**를 적습니다.

```ini
[client]
host=본인의-RDS-엔드포인트.ap-northeast-2.rds.amazonaws.com
port=3306
user=본인의_DB_사용자
password="본인의_DB_비밀번호"
ssl-ca=/home/ubuntu/.config/slotkey-samples/rds-ca.pem
```

MySQL 옵션 파일의 따옴표/역슬래시는 MySQL 규칙에 따라 이스케이프하세요. 예를 들어 비밀번호에 `\`가 들어가면 `\\`, `"`가 들어가면 `\"`로 적습니다. 파일을 `source`하지 않습니다.
스크립트는 파일 소유자·권한(400/600)을 검사하고 TCP 및 `VERIFY_IDENTITY`를 강제합니다. 비밀번호를 CLI 인자로 전달하거나 로그에 출력하지 않습니다.
[MySQL 옵션 파일](https://dev.mysql.com/doc/refman/8.4/en/option-files.html) · [AWS RDS 인증서](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/UsingWithRDS.SSL.html)

EC2 → RDS의 3306 연결이 허용되어야 합니다. 계정에는 대상 DB의 SELECT/INSERT/UPDATE/CREATE 및 CREATE ROUTINE/ALTER ROUTINE/EXECUTE 권한이 필요합니다. 전역 SUPER 권한이나 DB 삭제 권한은 요구하지 않습니다.

```bash
export AWS_SAMPLE_MYSQL_CNF="$HOME/.config/slotkey-samples/mysql.cnf"
export AWS_SAMPLE_DB_NAME=slotkey
# 서비스 설정과 같은 실제 저장 디렉터리를 지정합니다.
export SPACE_IMAGE_STORAGE_ROOT=/var/slotkey/space-images

bash data-setting/aws-setup.sh status

# 서비스의 관리자 작업/예약 요청과 겹치지 않게 잠시 중지합니다.
sudo systemctl stop slotkey.service
bash data-setting/aws-setup.sh setup
sudo systemctl start slotkey.service
```

`setup`에서 새 계정에 적용할 비밀번호를 숨김 입력으로 두 번 받습니다(12자 이상, UTF-8 72바이트 이하). 공개된 `00000000`을 기본값으로 사용하지 않습니다. 이미 생성된 계정에는 새 비밀번호를 덮어쓰지 않으므로 이전 계정은 기존 비밀번호를 사용합니다.
실패하면 오류를 확인하고 서비스를 다시 시작하세요. 성공한 DB 시딩은 이후 상태 조회 실패로 되돌려지지 않습니다.

무인 실행이 필요한 경우, BCrypt 해시만 담은 현재 사용자 소유의 600 파일을 만들어 `AWS_SAMPLE_PASSWORD_HASH_FILE`에 경로를 지정합니다. 저장소에 파일이나 비밀번호를 넣지 않습니다.

```bash
# 숨김 입력으로 600 해시 파일 생성. 기존 파일이 있으면 덮어쓰기를 거절합니다.
(umask 077; set -o noclobber; python3 data-setting/aws-password.py > "$HOME/.config/slotkey-samples/password.hash")
export AWS_SAMPLE_PASSWORD_HASH_FILE="$HOME/.config/slotkey-samples/password.hash"
```

## 재실행과 이미지 확인

위 연결 환경변수는 새 SSH 세션에서 다시 지정해야 합니다.
`setup`/`update`의 DB 잠금은 다른 샘플 스크립트만 직렬화합니다. 재실행 때도 서비스와 관리자 작업을 잠시 중지하세요.

```bash
bash data-setting/aws-setup.sh status
# 기존 데이터 표시만 수정할 때: 비밀번호 입력 없음
bash data-setting/aws-setup.sh update
# 이미지 파일만 복구할 때
bash data-setting/aws-setup.sh images
```

DB와 이미지 파일은 하나의 트랜잭션이 아닙니다. 파일을 먼저 설치하고 DB 작업을 실행합니다. DB 오류 시 이미 설치한 이미지는 남으며 재실행할 수 있습니다. 기존 파일이 원본과 다르거나 심볼릭 링크이면 덮어쓰지 않습니다. 비정상 종료로 `.slotkey-aws-install-lock`이 남았다면 실행 프로세스가 없는지 확인 후 해당 설치 잠금 디렉터리만 정리하세요.

이미지는 UUID 형식 `a5100000-0000-4000-8000-000000000001.jpg` ~ `...000039.jpg`로 설치됩니다. 현재 백엔드의 공개 이미지 API를 사용하므로 Vercel 빌드에 사진을 복사하거나 사진 추가 때문에 재배포할 필요가 없습니다. 서비스 사용자가 파일을 읽을 수 있어야 합니다.

```bash
curl -i 'https://slotkey.store/api/v1/spaces?page=0&size=3'
curl --fail -o /dev/null -w '%{http_code}\n' \
  'https://slotkey.store/api/v1/space-images/a5100000-0000-4000-8000-000000000001.jpg'
```

초기 상태 기준 `status`는 일반 회원 100명·관리자 1명·공간 39개·지역별 13개를 표시합니다. 예약은 자동 생성하지 않습니다.

## 검증 실행

```bash
bash -n data-setting/aws-setup.sh
# CLI·비밀번호 검사 (MySQL 컨테이너가 없으면 DB 검사 skipped)
python3 data-setting/tests/test_aws_setup.py
# 개발 DB와 분리한 임시 MySQL 컨테이너에서 전체 검사
docker run --rm -d --name slotkey-aws-seed-check \
  -e MYSQL_ALLOW_EMPTY_PASSWORD=yes mysql:8.4
# MySQL 시작 완료 후 실행
python3 data-setting/tests/test_aws_setup.py --mysql-container slotkey-aws-seed-check
docker stop slotkey-aws-seed-check
```

테스트는 지정한 컨테이너에 임의 이름의 검증 DB를 생성·정리합니다. 실제 RDS TLS 연결과 배포 화면은 이 검증에 포함하지 않습니다.
비밀번호 검사는 `bcrypt`가 없으면 skipped로 표시되며, 임시 설치 경로를 `--bcrypt-path`로 지정할 수도 있습니다.
