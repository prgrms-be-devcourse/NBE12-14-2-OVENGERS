# 팀 공통 샘플 데이터

## 구조

저장소 최상위의 backend, frontend와 나란히 data-setting을 둡니다.
이 폴더 안에 setup.sh, sql/, images/(원본 39장), photos.html, space-map.json이 모두 들어 있습니다.
압축을 최상위에 풀면 data-setting 폴더가 생성됩니다. data-setting 안에서 풀면 중첩되므로 최상위에서 해제하세요.

```bash
unzip -n ~/Downloads/base-data-kit.zip -d .
```

## 실행

Docker/MySQL 실행 → 백엔드를 한 번 실행해 Flyway V8까지 적용 → 백엔드 종료(MySQL 유지) → 아래 명령 실행 → 서버 재시작 순서입니다.

```bash
# 회원/공간 DB 데이터 및 프론트 사진 설치
bash data-setting/setup.sh setup
# 샘플 DB 개수 조회
bash data-setting/setup.sh status
# 샘플 DB 데이터와 관련 테스트 기록 + 설치한 프론트 사진 삭제
bash data-setting/setup.sh clean
# 삭제 후 처음 상태로 다시 생성
bash data-setting/setup.sh reset
# 사진과 가상 공간 이름 미리 보기 (macOS)
open data-setting/photos.html
```

clean/reset은 DB 이름을 입력해야 진행합니다. 무인 실행 시에만 두 번째 인자 --yes를 사용하세요.
setup/clean/reset은 동시에 실행하지 않습니다. 데이터베이스와 파일 작업은 단일 트랜잭션이 아닙니다. DB 실패 시 설치 사진이 남을 수 있으며, 문제를 해결한 뒤 setup 또는 clean을 다시 실행할 수 있습니다.

## 데이터

- 테스트 일반 회원 100명: sample.user001@example.com ~ sample.user100@example.com
- 공통 초기 비밀번호: Sample1234! (DB에는 BCrypt 해시)
- USER / ACTIVE, 초기 1,000,000 크레딧 및 동일 금액 SIGNUP_GRANT 원장.
- 판교 13개 / 하남 13개 / 강남 13개, 총 39개 공간, 사진도 중복 없이 한 장씩.
- 공간명은 사진 특징을 바탕으로 만든 가상 이름이며 실제 업체/촬영지를 의미하지 않습니다.
- 신규 공간 운영 08:00~23:00, 30분당 2,000~6,500원, 수용인원 2~12명은 테스트용 값입니다.
- 관리자나 예약은 자동 생성하지 않습니다. 샘플 계정으로 로그인해 예약하세요.
- setup 재실행은 회원/예약/잔액을 초기화하지 않습니다. 없는 샘플을 추가하고 등록된 공간 이름·사진 경로가 달라진 경우 해당 필드 및 version만 갱신합니다.

## 정리 범위

clean은 slotkey_sample_registry에 기록된 샘플 회원과 공간, 샘플 회원 예약·슬롯·상태 이력·출입키·리프레시 토큰·멱등성 기록·크레딧 원장·관련 출입 로그·샘플을 대상으로 한 감사 로그를 삭제합니다.
일반 회원의 예약/원장 등이 샘플 데이터와 연결되어 있으면 삭제를 중단합니다. 알 수 없는 FK가 추가된 경우에도 DB 삭제는 실패하고 롤백됩니다.
샘플 회원이 일반 대상에 남긴 감사 로그는 보존됩니다. 자동 증가 ID는 되돌리지 않습니다.
**DB 자체, 테이블, Flyway 이력, 일반 회원/공간은 삭제하지 않습니다.** 빈 slotkey_sample_registry 관리 테이블은 남습니다.

사진은 setup이 frontend/public/images/slotkey-test-data/에 복사합니다. clean은 동일한 원본과 일치하는 설치 사진 및 소유 표시 파일만 삭제합니다. 사진을 수동 수정했거나 소유 표시가 없다면 삭제 전 중단합니다. 다른 파일은 삭제하지 않습니다.
재설치용 data-setting/images 원본 및 SQL/스크립트는 clean 후에도 남습니다.

## 환경

기본 로컬 Docker 컨테이너: slotkey-mysql / DB: slotkey.
컨테이너의 MYSQL_ROOT_PASSWORD 또는 MYSQL_ROOT_PASSWORD_FILE을 통해 연결합니다.

```bash
SAMPLE_DB_CONTAINER=컨테이너이름 SAMPLE_DB_NAME=slotkey bash data-setting/setup.sh setup
```

로컬 Docker 소켓만 지원합니다. 원격 DB 전체 초기화 도구가 아닙니다.
Docker CLI와 Bash가 필요하며 호스트 Python/MySQL CLI는 필요 없습니다.

## 이전 키트에서 이동

- 이전 등록 데이터를 같은 레지스트리로 인식합니다. 이미 39개라면 setup으로 새 사진 경로를 반영합니다.
- 이전 60개가 있다면 setup이 중단됩니다. 샘플 관련 예약까지 삭제해도 된다면 새 명령의 reset으로 39개를 재생성하세요.
- 예전 frontend/public/images/sample-spaces/ 사진과 최상위 BaseDataSetting*.sh, scripts/base-data, 안내문은 이번 명령이 자동 삭제하지 않습니다. 예전 키트 파일만인지 확인한 뒤 별도로 정리하세요. 새 clean은 이번 전용 경로의 사진을 관리합니다.

## 팀 공유 / PR

원본과 스크립트가 있는 data-setting만 커밋합니다. 설치된 프론트 사진은 각자 setup으로 만듭니다.
저장소 최상위 .gitignore에 아래 줄을 한 번 추가하세요.

```gitignore
/frontend/public/images/slotkey-test-data/
```

```bash
git add data-setting .gitignore
git commit -m "feat(dev): add sample data setup and cleanup toolkit"
```

팀원은 PR 머지 후 코드를 받고 setup을 실행합니다. 브랜치 이동/클론으로 DB가 자동 변경되지는 않습니다.

## 검증

이미지 39장/지역별 13개 매핑, Bash 문법 및 가짜 Docker를 사용한 파일 설치·재실행·정리·충돌 차단 흐름을 확인했습니다.
실제 MySQL 실행 환경이 없어 SQL을 실제 DB에서 실행한 검증은 하지 못했습니다.
