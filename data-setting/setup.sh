#!/usr/bin/env bash
# 로컬 Docker MySQL 전용. 프로젝트 루트에서 bash data-setting/setup.sh setup
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONTAINER="${SAMPLE_DB_CONTAINER:-slotkey-mysql}"
DATABASE="${SAMPLE_DB_NAME:-slotkey}"
ACTION="${1:-help}"
case "$ACTION" in setup|status|clean|reset) ;; *)
  echo '사용: bash data-setting/setup.sh {setup|status|clean|reset} [--yes]'
  echo 'setup: 샘플 추가 / status: 개수 확인 / clean: 샘플 및 관련 테스트 기록 삭제 / reset: 삭제 후 재생성'
  exit 0;; esac
PROJECT_ROOT="$(cd "$ROOT/.." && pwd)"
PUBLIC="$PROJECT_ROOT/frontend/public"
TARGET="$PUBLIC/images/slotkey-test-data"
MARKER="$TARGET/.slotkey-data-setting-owned"
[[ -d "$PROJECT_ROOT/backend" && -d "$PUBLIC" ]] || { echo 'data-setting 폴더를 backend/frontend와 같은 위치에 두세요.' >&2; exit 1; }
# 경로가 외부 디렉터리로 연결되는 것을 방지합니다.
for part in "$PROJECT_ROOT/frontend" "$PUBLIC" "$PUBLIC/images" "$TARGET"; do
  [[ ! -L "$part" ]] || { echo "심볼릭 링크 경로는 지원하지 않습니다: $part" >&2; exit 1; }
done
LOCK="$ROOT/.run-lock"
mkdir "$LOCK" 2>/dev/null || { echo '다른 세팅 명령 실행 중입니다. 비정상 종료였다면 실행 프로세스가 없는지 확인 후 data-setting/.run-lock을 지우세요.' >&2; exit 1; }
trap 'rmdir "$LOCK" 2>/dev/null || true' EXIT
check_images() {
  [[ -e "$TARGET" ]] || return 0
  [[ -d "$TARGET" && -f "$MARKER" && ! -L "$MARKER" ]] && grep -qx 'slotkey-data-setting-v1' "$MARKER" || {
    echo '사진 경로에 이 도구가 소유하지 않은 파일이 있습니다. 덮어쓰거나 삭제하지 않습니다.' >&2; return 1;
  }
  local src dst
  for src in "$ROOT"/images/space-*.jpg; do
    dst="$TARGET/$(basename "$src")"
    [[ ! -L "$dst" ]] || { echo "사진 링크 충돌: $dst" >&2; return 1; }
    if [[ -e "$dst" ]] && ! cmp -s "$src" "$dst"; then
      echo "설치 후 변경된 사진을 발견했습니다. 보관하거나 원본으로 복원 후 재시도: $dst" >&2; return 1
    fi
  done
}
install_images() {
  check_images
  mkdir -p "$TARGET"
  printf '%s\n' 'slotkey-data-setting-v1' > "$MARKER"
  local src
  for src in "$ROOT"/images/space-*.jpg; do cp "$src" "$TARGET/$(basename "$src")"; done
  echo '프론트 사진 39장 설치 완료'
}
clean_images() {
  [[ -d "$TARGET" ]] || return 0
  check_images
  local src
  for src in "$ROOT"/images/space-*.jpg; do rm -f "$TARGET/$(basename "$src")"; done
  rm -f "$MARKER"
  rmdir "$TARGET" 2>/dev/null || echo '다른 파일이 남아 사진 폴더는 유지했습니다.'
  echo '설치한 프론트 사진 정리 완료'
}
if [[ "$ACTION" != status ]]; then check_images; fi
[[ "$DATABASE" =~ ^[a-zA-Z0-9_]+$ ]] || { echo 'DB 이름은 영문/숫자/밑줄만 가능합니다.' >&2; exit 1; }
command -v docker >/dev/null || { echo 'Docker Desktop을 설치하고 실행해주세요.' >&2; exit 1; }
DOCKER_ENDPOINT="${DOCKER_HOST:-$(docker context inspect --format '{{.Endpoints.docker.Host}}')}"
case "$DOCKER_ENDPOINT" in unix://*|npipe://*) ;; *) echo '로컬 Docker 소켓에서만 실행 가능합니다.' >&2; exit 1;; esac
[[ "$(docker inspect --format '{{.State.Running}}' "$CONTAINER")" == true ]] || { echo 'MySQL 컨테이너를 먼저 실행해주세요.' >&2; exit 1; }

mysql_run() {
  # 비밀번호는 컨테이너 내부 환경변수/파일로만 읽습니다. 명령 인자나 로그에 출력하지 않습니다.
  docker exec -i "$CONTAINER" sh -c '
    if [ -n "${MYSQL_ROOT_PASSWORD_FILE:-}" ]; then
      MYSQL_PWD=$(cat "$MYSQL_ROOT_PASSWORD_FILE")
    else
      MYSQL_PWD=${MYSQL_ROOT_PASSWORD:-}
    fi
    export MYSQL_PWD
    exec mysql --user=root --default-character-set=utf8mb4 --batch --database="$1"
  ' sh "$DATABASE"
}
# 서버 시작으로 Flyway가 최신 테이블을 만든 뒤 사용할 수 있습니다.
mysql_run <<'SQL'
SELECT COUNT(*) AS schema_check FROM member;
SELECT COUNT(*) AS schema_check FROM spaces;
SELECT COUNT(*) AS schema_check FROM credit_transaction;
CREATE TABLE IF NOT EXISTS slotkey_sample_registry (
  kind VARCHAR(20) NOT NULL,
  seed_key VARCHAR(100) NOT NULL,
  row_id BIGINT NOT NULL,
  PRIMARY KEY (kind, seed_key),
  UNIQUE KEY uq_sample_row (kind, row_id)
) ENGINE=InnoDB;
SQL

run_sql() {
  local operation="$1"
  local routine="slotkey_sample_${operation}_$$_${RANDOM}"
  local result=0
  local sql_file="$ROOT/sql/$operation.sql"
  sed "s/__ROUTINE__/$routine/g" "$sql_file" | mysql_run || result=$?
  # 실패 시에도 임시 프로시저를 정리합니다. 데이터 삭제를 위한 DROP이 아닙니다.
  printf 'DROP PROCEDURE IF EXISTS `%s`;\n' "$routine" | mysql_run || true
  return "$result"
}
status() { mysql_run < "$ROOT/sql/status.sql"; }
echo "대상: 로컬 Docker $CONTAINER / DB $DATABASE"
if [[ "$ACTION" == clean || "$ACTION" == reset ]]; then
  status
  echo '등록된 샘플 계정·공간과 샘플 계정의 예약/토큰/크레딧/관련 로그를 삭제합니다.'
  echo '일반 계정이 샘플 공간을 예약한 경우에는 삭제를 중단합니다.'
  echo '작업 전 백엔드를 종료해 예약/스케줄러 작업과 겹치지 않게 해주세요.'
  if [[ "${2:-}" != --yes ]]; then
    read -r -p "계속하려면 DB 이름 '$DATABASE' 입력: " answer
    [[ "$answer" == "$DATABASE" ]] || { echo '취소했습니다.'; exit 1; }
  fi
  run_sql clean
  clean_images
fi
if [[ "$ACTION" == setup || "$ACTION" == reset ]]; then
  install_images
  if ! run_sql setup; then
    echo 'DB 세팅 실패. 설치한 사진은 남아 있습니다. 원인 해결 후 setup을 다시 실행하거나 clean으로 정리하세요.' >&2
    exit 1
  fi
fi
status
echo '완료. 사진 경로: frontend/public/images/slotkey-test-data/'
