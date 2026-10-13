CREATE TABLE charge_order (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id   VARCHAR(64) NOT NULL,
    member_id  BIGINT NOT NULL,
    amount     BIGINT NOT NULL,
    status     VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT uk_charge_order_order_id UNIQUE (order_id),
    CONSTRAINT fk_charge_order_member FOREIGN KEY (member_id) REFERENCES member (id)
) ENGINE = InnoDB;
