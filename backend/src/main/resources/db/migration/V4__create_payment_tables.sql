CREATE TABLE payment  (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reservation_id BIGINT NOT NULL ,
    amount BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    paid_at DATETIME(6) NOT NULL,
    cancelled_at DATETIME(6),

    PRIMARY KEY (id),
    UNIQUE (reservation_id),
    FOREIGN KEY (reservation_id) REFERENCES reservation(id)
);