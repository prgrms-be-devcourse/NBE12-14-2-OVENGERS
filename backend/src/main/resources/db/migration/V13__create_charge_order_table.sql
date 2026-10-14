CREATE TABLE charge_order (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id   VARCHAR(64) NOT NULL,
    member_id  BIGINT NOT NULL,
    amount     BIGINT NOT NULL,
    status     VARCHAR(20) NOT NULL,
    payment_key VARCHAR(200) NULL,
    client_idempotency_key VARCHAR(100) NULL,
    pg_idempotency_key VARCHAR(100) NULL,
    failure_code VARCHAR(100) NULL,
    recovery_next_attempt_at DATETIME NULL,
    recovery_exhausted_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uk_charge_order_order_id UNIQUE (order_id),
    CONSTRAINT uk_charge_order_payment_key UNIQUE (payment_key),
    CONSTRAINT uk_charge_order_member_client_idempotency
        UNIQUE (member_id, client_idempotency_key),
    CONSTRAINT uk_charge_order_pg_idempotency_key UNIQUE (pg_idempotency_key),
    CONSTRAINT fk_charge_order_member FOREIGN KEY (member_id) REFERENCES member (id),
    INDEX idx_charge_order_recovery_due
        (status, recovery_exhausted_at, recovery_next_attempt_at, id),
    INDEX idx_charge_order_recovery_exhausted
        (status, recovery_exhausted_at, updated_at, id)
) ENGINE = InnoDB;

ALTER TABLE credit_transaction
    ADD COLUMN charge_order_id BIGINT NULL,
    ADD CONSTRAINT uk_credit_transaction_charge_order UNIQUE (charge_order_id),
    ADD CONSTRAINT fk_credit_transaction_charge_order
        FOREIGN KEY (charge_order_id) REFERENCES charge_order (id);
