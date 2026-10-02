#!/usr/bin/env bash
# EC2 + RDS 전용. 로컬 Docker 도구와 삭제 경로를 공유하지 않습니다.
set +x
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ACTION="${1:-help}"
case "$ACTION" in
  setup|update|status|images) ;;
  help|--help|-h)
    echo '사용: bash data-setting/aws-setup.sh {setup|update|status|images}'
    echo 'setup: 없는 샘플 추가 + 표시 정보 갱신 / update: 기존 샘플 표시 정보만 갱신'
    echo 'status: AWS 샘플 개수 조회 / images: EC2 이미지 설치(DB 연결 없음)'
    exit 0 ;;
  *) echo "지원하지 않는 명령: $ACTION" >&2; exit 1 ;;
esac
[[ $# -eq 1 ]] || { echo '명령 하나만 지정하세요.' >&2; exit 1; }
fail() { echo "$*" >&2; exit 1; }
DATABASE="${AWS_SAMPLE_DB_NAME:-slotkey}"
STORAGE="${SPACE_IMAGE_STORAGE_ROOT:-/var/slotkey/space-images}"
SQL_TEMP=''
ROUTINE=''
IMAGE_LOCK=''
cleanup() {
  local result=$?
  trap - EXIT
  if [[ -n "$ROUTINE" ]]; then
    printf 'DROP PROCEDURE IF EXISTS `%s`;\n' "$ROUTINE" | mysql_run >/dev/null || {
      echo "임시 프로시저 정리 실패: $ROUTINE" >&2
      result=1
    }
  fi
  [[ -z "$SQL_TEMP" ]] || rm -f -- "$SQL_TEMP"
  if [[ -n "$IMAGE_LOCK" ]]; then
    rm -f -- "$IMAGE_LOCK/image.jpg"
    rmdir "$IMAGE_LOCK" 2>/dev/null || true
  fi
  exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

private_file() {
  local file="$1" mode
  [[ -f "$file" && ! -L "$file" && -r "$file" && -O "$file" ]] ||
    fail "현재 사용자 소유의 일반 파일이 필요합니다: $file"
  mode="$(stat -c '%a' "$file" 2>/dev/null || stat -f '%Lp' "$file")"
  [[ "$mode" == 600 || "$mode" == 400 ]] || fail "파일 권한을 600 또는 400으로 지정하세요: $file"
}

install_images() {
  [[ "$STORAGE" == /* && -d "$STORAGE" && -w "$STORAGE" ]] ||
    fail 'SPACE_IMAGE_STORAGE_ROOT를 기존의 쓰기 가능한 절대 디렉터리로 지정하세요.'
  local parent="$STORAGE" n src dst
  while [[ "$parent" != / ]]; do
    [[ ! -L "$parent" ]] || fail "이미지 저장 경로의 심볼릭 링크는 지원하지 않습니다: $parent"
    parent="$(dirname "$parent")"
  done
  IMAGE_LOCK="$STORAGE/.slotkey-aws-install-lock"
  mkdir "$IMAGE_LOCK" 2>/dev/null || { IMAGE_LOCK=''; fail '다른 AWS 이미지 설치가 실행 중입니다.'; }
  # 먼저 39장 전체를 검사합니다. 다른 파일/수정된 업로드를 덮어쓰지 않습니다.
  for ((n=1; n<=39; n++)); do
    printf -v src '%s/images/space-%02d.jpg' "$ROOT" "$n"
    printf -v dst '%s/a5100000-0000-4000-8000-%012d.jpg' "$STORAGE" "$n"
    [[ -f "$src" && ! -L "$src" ]] || fail "원본 이미지 누락: $src"
    [[ ! -L "$dst" ]] || fail "이미지 심볼릭 링크 충돌: $dst"
    if [[ -e "$dst" ]]; then
      [[ -f "$dst" ]] && cmp -s "$src" "$dst" || fail "다른 내용의 이미지가 존재합니다: $dst"
    fi
  done
  for ((n=1; n<=39; n++)); do
    printf -v src '%s/images/space-%02d.jpg' "$ROOT" "$n"
    printf -v dst '%s/a5100000-0000-4000-8000-%012d.jpg' "$STORAGE" "$n"
    if [[ ! -e "$dst" ]]; then
      cp "$src" "$IMAGE_LOCK/image.jpg"
      chmod 644 "$IMAGE_LOCK/image.jpg"
      mv -n "$IMAGE_LOCK/image.jpg" "$dst"
      [[ ! -e "$IMAGE_LOCK/image.jpg" ]] || fail "설치 중 이미지 충돌: $dst"
    fi
    [[ -r "$dst" ]] || fail "이미지 읽기 권한이 없습니다: $dst"
  done
  rmdir "$IMAGE_LOCK"
  IMAGE_LOCK=''
  echo 'EC2 이미지 39장 설치 확인 완료 (/api/v1/space-images/UUID.jpg)'
}
if [[ "$ACTION" == images ]]; then install_images; exit 0; fi

[[ "$DATABASE" =~ ^[a-zA-Z0-9_]{1,48}$ ]] || fail 'DB 이름은 1~48자 영문/숫자/밑줄만 가능합니다.'
command -v mysql >/dev/null || fail 'MySQL CLI가 필요합니다. Ubuntu: sudo apt install mysql-client'
CONFIG="${AWS_SAMPLE_MYSQL_CNF:-}"
[[ "$CONFIG" == /* ]] || fail 'AWS_SAMPLE_MYSQL_CNF에 저장소 밖 MySQL 옵션 파일의 절대 경로를 지정하세요.'
private_file "$CONFIG"
mysql_run() {
  # defaults-file은 첫 옵션이어야 합니다. 기존 로그인 파일과 MYSQL_PWD의 혼입을 차단합니다.
  env -u MYSQL_PWD -u MYSQL_TEST_LOGIN_FILE mysql --defaults-file="$CONFIG" --no-login-paths \
    --protocol=TCP --ssl-mode=VERIFY_IDENTITY --connect-timeout=10 \
    --default-character-set=utf8mb4 --batch --database="$DATABASE" "$@"
}
echo "대상 DB: $DATABASE (옵션 파일의 host 사용, TLS 서버 검증 필수)"
mysql_run <<'SQL'
SELECT DATABASE() AS target_database, @@hostname AS server_hostname;
SELECT COUNT(*) AS schema_check FROM member;
SELECT COUNT(*) AS schema_check FROM spaces;
SELECT COUNT(*) AS schema_check FROM credit_transaction;
SELECT COUNT(*) AS schema_check FROM reservation;
SQL
LATEST="$(mysql_run --skip-column-names <<'SQL'
SELECT IF(COUNT(*) > 0 AND SUM(success=0)=0,
  COALESCE(MAX(CAST(version AS UNSIGNED)),0),0) FROM flyway_schema_history;
SQL
)"
[[ "$LATEST" =~ ^[0-9]+$ && "$LATEST" -ge 12 ]] || fail '백엔드를 실행해 Flyway V12 이상을 먼저 적용하세요.'

status() {
  local exists
  exists="$(mysql_run --skip-column-names <<'SQL'
SELECT COUNT(*) FROM information_schema.tables
WHERE table_schema=DATABASE() AND table_name='slotkey_sample_registry';
SQL
)"
  if [[ "$exists" == 0 ]]; then echo 'AWS 샘플이 아직 등록되지 않았습니다.'; return; fi
  mysql_run < "$ROOT/sql/aws_status.sql"
}
if [[ "$ACTION" == status ]]; then status; exit 0; fi

HASH=''
if [[ "$ACTION" == setup ]]; then
  if [[ -n "${AWS_SAMPLE_PASSWORD_HASH_FILE:-}" ]]; then
    private_file "$AWS_SAMPLE_PASSWORD_HASH_FILE"
    HASH="$(cat "$AWS_SAMPLE_PASSWORD_HASH_FILE")"
  else
    command -v python3 >/dev/null || fail 'Python 3 또는 AWS_SAMPLE_PASSWORD_HASH_FILE이 필요합니다.'
    HASH="$(python3 "$ROOT/aws-password.py")"
  fi
  [[ "$HASH" =~ ^\$2[aby]\$(0[4-9]|[12][0-9]|3[01])\$[./A-Za-z0-9]{53}$ ]] || fail '유효한 BCrypt 해시가 필요합니다.'
fi
install_images
mysql_run <<'SQL'
CREATE TABLE IF NOT EXISTS slotkey_sample_registry (
  kind VARCHAR(20) NOT NULL,
  seed_key VARCHAR(100) NOT NULL,
  row_id BIGINT NOT NULL,
  PRIMARY KEY (kind, seed_key),
  UNIQUE KEY uq_sample_row (kind, row_id)
) ENGINE=InnoDB;
SQL
umask 077
SQL_TEMP="$(mktemp "${TMPDIR:-/tmp}/slotkey-aws-sql.XXXXXX")"
ROUTINE="slotkey_aws_sample_$$_${RANDOM}"
{
  printf "SET @aws_sample_create_missing = %s;\n" "$([[ "$ACTION" == setup ]] && echo 1 || echo 0)"
  printf "SET @aws_sample_password_hash = '%s';\n" "$HASH"
  sed "s/__ROUTINE__/$ROUTINE/g" "$ROOT/sql/aws_setup.sql"
} > "$SQL_TEMP"
mysql_run < "$SQL_TEMP"
status
echo '완료. 기존 회원·비밀번호·잔액·예약 및 공간 가격/영업시간은 보존됩니다.'
