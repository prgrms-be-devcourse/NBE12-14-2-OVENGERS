# 트러블슈팅 가이드

> **문서 목적 및 용도**: 본 문서는 Slot Key 시스템의 개발, 테스트 및 운영 과정에서 발생한 결함·장애·예외 상황에 대해 재현 환경, 근본 원인(Root Cause), 확인/재현 명령, 그리고 해결책(Resolution)을 명확한 증거와 함께 기록하는 공식 기술 문서입니다.

---

## 1. 운영 및 공식 장애 등록 현황

현재 단계에서는 프로덕션 운영 환경 장애나 별도 인시던트 티켓으로 격리 검증된 공식 프로덕션 장애 사례는 등록되어 있지 않습니다. 가상의 장애나 검증되지 않은 추측성 해결책은 본 문서에 수록하지 않습니다.

향후 실제 배포 및 운영 단계에서 장애나 특이 결함이 발생할 경우, 아래 템플릿에 맞추어 검증된 내용만 추가 등록합니다.

---

## 2. 개발 및 테스트 환경 주요 확인 사항

로컬 개발 및 테스트 실행 시 빈번하게 문의되거나 주의가 필요한 기술적 검증 사항을 정리합니다.

### 2.1. Docker 데몬 미실행 시 Testcontainers 통합 테스트 실패
- **현상**: `./gradlew test` 실행 시 Testcontainers 관련 통합 테스트 클래스(`*IntegrationTest`, `*ConcurrencyTest`)가 실패하거나 대기 상태에 빠짐.
- **원인**: 통합 테스트는 실제 MySQL 8.4 컨테이너를 구동하여 UNIQUE 제약, 비관적 락(`SELECT ... FOR UPDATE`), 조건부 UPDATE를 검증하므로 Docker 데몬이 필수적임.
- **확인 명령**:
  ```bash
  docker ps
  ```
- **해결책**:
  - Docker Desktop 또는 Docker 데몬을 실행한 후 테스트를 재수행합니다.
  - Docker 데몬을 구동할 수 없는 환경에서는 Testcontainers 기반 통합 테스트가 제한되며, 단위 테스트만 격리 실행해야 합니다.

### 2.2. 예약 결제 및 연장 동시성 경합 시 데드락(Deadlock) 방지
- **현상**: 동일 공간이나 슬롯에 대해 결제와 연장 요청이 동시에 인입될 때 MySQL 트랜잭션 교착 상태(Deadlock) 발생 위험.
- **원인**: 여러 엔티티에 대한 비관적 락 획득 순서가 일치하지 않을 경우 DB 수준의 상호 대기가 발생할 수 있음.
- **해결 및 예방책**:
  - 시스템 전반에 걸쳐 항상 **`Space` 잠금 획득 후 `Reservation` 잠금 획득**이라는 단방향 락 프로토콜을 강제합니다.
  - 상세 내용은 [`docs/decisions/reservation-concurrency.md`](decisions/reservation-concurrency.md)를 참고합니다.

### 2.3. 공간 대표 사진 업로드 크기 제한 (`MaxUploadSizeExceededException`)
- **현상**: 관리자 공간 사진 업로드(`PUT /api/v1/admin/spaces/{spaceId}/image`) 시 5MB를 초과하는 파일을 첨부하면 요청이 거절됨.
- **원인**: 백엔드 `application.yml` 설정(`spring.servlet.multipart.max-file-size: 5MB`, `max-request-size: 6MB`) 및 `ErrorCode.IMAGE_SIZE_EXCEEDED` 규약에 도달함.
- **해결 및 처리**:
  - 프론트엔드(`frontend/src/utils/imageValidation.ts`)에서 `MAX_IMAGE_FILE_SIZE = 5 * 1024 * 1024`(5MB) 초과 파일 첨부를 사전 차단합니다.
  - 백엔드 `GlobalExceptionHandler`에서 `MaxUploadSizeExceededException`을 포착하여 **413 Payload Too Large** (`IMAGE_SIZE_EXCEEDED`, "이미지 파일 크기는 최대 5MB까지 가능합니다.")로 규격화 응답합니다 ([`AdminSpaceImageMultipartLimitIntegrationTest`](../backend/src/test/java/com/ovengers/slotkey/space/controller/AdminSpaceImageMultipartLimitIntegrationTest.java) 검증 완료).
  - 상세 내용은 [`docs/admin-space-image-upload.md`](admin-space-image-upload.md)를 참고합니다.

### 2.4. 동일 `Idempotency-Key` 재요청 시 처리
- **현상**: 네트워크 순단으로 클라이언트가 동일한 `Idempotency-Key`로 결제 요청을 재시도할 때 중복 결제 또는 에러 발생 우려.
- **원인**: 멱등성 테이블(`idempotency_key`)에 성공 응답(200 OK)만 캐싱하도록 설계되어 있음.
- **해결책**:
  - 이미 성공한 키로 재요청 인입 시 실제 결제 로직을 재수행하지 않고 보관된 200 성공 응답을 즉시 반환합니다.
  - 실패(예: 잔액 부족 422)한 키로 재요청 시에는 캐시가 없어 재시도가 정상 허용됩니다.
  - 상세 내용은 [`docs/decisions/reservation-idempotency.md`](decisions/reservation-idempotency.md)를 참고합니다.

---

## 3. 관련 실행 및 설정 문서 안내

세부적인 실행 방법, 빌드 오류 및 환경 변수 설정에 관한 지침은 아래 문서를 참조하십시오:

- 백엔드 실행 및 환경 변수 안내: [`backend/README.md`](../backend/README.md)
- 프론트엔드 개발 서버 및 프록시 설정: [`frontend/README.md`](../frontend/README.md)
- 데이터베이스 초기화 및 시드 데이터: [`data-setting/README.md`](../data-setting/README.md)
- 테스트 실행 전략 및 소스 매핑: [`docs/test-strategy.md`](test-strategy.md)
- 전체 테스트 실행 결과 집계: [`docs/test-results.md`](test-results.md)