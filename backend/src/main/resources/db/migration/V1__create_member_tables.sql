# 천종원님
# TODO(2026-09-15, 이태호): member 테이블이 아직 작성되지 않음 (섹션 11 적용 전 상태로 비워둠).
#   core-domain-decisions.md §11에 따라 실제 CREATE TABLE 작성 시 아래를 반영해야 함:
#   - member.balance (INT, NOT NULL, DEFAULT 0) 컬럼 포함
#   - 테이블명은 반드시 `member` (단수)로 생성할 것
#     → V3(reservation)와 V6(credit_transaction)의 FK가 이미 `member (id)`를 참조하고 있음
#     → V2가 `spaces`(복수)로 만들어져 있던 것과 같은 실수가 반복되면 마이그레이션이 실패함
#   참고 필드 목록(docs/erd.md): id, email, password_hash, nickname, role, status, balance, created_at
