# 백한비님
# 2026-09-15 확인(이태호, core-domain-decisions.md §11): payment 테이블은 삭제 대상으로 확정됨.
#   이 파일이 아직 비어 있어(= payment 테이블이 한 번도 생성되지 않음) 실제로 DROP할 대상이 없음.
#   → 이 마이그레이션 파일에는 아무것도 작성하지 않는다. payment의 역할은 V6(credit_transaction)로 대체됨.
#   → 새 결제/크레딧 로직은 V6__create_credit_transaction_table.sql 참고.
