-- 일시: 2026-10-16
-- 기능: 충전 내역에 결제수단 표시
-- 토스 승인 응답의 method(카드·간편결제 등)와 easyPay.provider(토스페이 등)를 저장한다.
-- 기존 주문과 복구 경로로 승인된 주문은 값이 없으므로 NULL을 허용한다.
ALTER TABLE charge_order
    ADD COLUMN payment_method VARCHAR(30) NULL,
    ADD COLUMN payment_provider VARCHAR(30) NULL;
