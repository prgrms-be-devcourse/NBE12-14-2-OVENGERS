CREATE TABLE door_access_token (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reservation_id BIGINT NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    issued_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    revoke_reason VARCHAR(100) NULL,

    active_reservation_id BIGINT
        GENERATED ALWAYS AS (
        CASE
            WHEN revoked_at IS NULL THEN reservation_id
            ELSE NULL
        END
        ) STORED,

    PRIMARY KEY (id),

    CONSTRAINT uk_door_access_token_token_hash
        UNIQUE (token_hash),

    CONSTRAINT uk_door_access_token_active_reservation
        UNIQUE (active_reservation_id),

    CONSTRAINT fk_door_access_token_reservation
        FOREIGN KEY (reservation_id)
            REFERENCES reservation (id)
);

CREATE TABLE door_access_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_member_id BIGINT NULL,
    reservation_id BIGINT NULL,
    requested_space_id BIGINT NULL,
    result VARCHAR(20) NOT NULL,
    reason_code VARCHAR(50) NULL,
    attempted_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT fk_door_access_log_actor_member
        FOREIGN KEY (actor_member_id)
            REFERENCES `member` (id),

    CONSTRAINT fk_door_access_log_reservation
        FOREIGN KEY (reservation_id)
            REFERENCES reservation (id),

    CONSTRAINT fk_door_access_log_requested_space
        FOREIGN KEY (requested_space_id)
            REFERENCES space (id)
);