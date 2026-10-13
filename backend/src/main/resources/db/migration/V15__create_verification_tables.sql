CREATE TABLE verification_guard (
                                    id BIGINT NOT NULL AUTO_INCREMENT,
                                    email VARCHAR(254) NOT NULL,
                                    purpose VARCHAR(30) NOT NULL,
                                    failed_attempts INT NOT NULL DEFAULT 0,
                                    block_stage INT NOT NULL DEFAULT 0,
                                    blocked_until DATETIME(6) NULL,
                                    admin_locked BOOLEAN NOT NULL DEFAULT FALSE,
                                    last_sent_at DATETIME(6) NULL,

                                    PRIMARY KEY (id),

                                    CONSTRAINT uk_verification_guard_email_purpose
                                        UNIQUE (email, purpose),

                                    CONSTRAINT chk_verification_guard_attempts
                                        CHECK (failed_attempts BETWEEN 0 AND 5),

                                    CONSTRAINT chk_verification_guard_stage
                                        CHECK (block_stage BETWEEN 0 AND 3)
);

CREATE TABLE verification_challenge (
                                        id BIGINT NOT NULL AUTO_INCREMENT,
                                        guard_id BIGINT NOT NULL,
                                        code_hash VARCHAR(64) NOT NULL,
                                        issued_at DATETIME(6) NOT NULL,
                                        expires_at DATETIME(6) NOT NULL,
                                        verified_at DATETIME(6) NULL,
                                        invalidated_at DATETIME(6) NULL,

                                        PRIMARY KEY (id),

                                        CONSTRAINT fk_verification_challenge_guard
                                            FOREIGN KEY (guard_id)
                                                REFERENCES verification_guard (id),

                                        INDEX idx_verification_challenge_guard_id (guard_id, id)
);

CREATE TABLE verification_grant (
                                    id BIGINT NOT NULL AUTO_INCREMENT,
                                    challenge_id BIGINT NOT NULL,
                                    grant_hash VARCHAR(64) NOT NULL,
                                    issued_at DATETIME(6) NOT NULL,
                                    expires_at DATETIME(6) NOT NULL,
                                    consumed_at DATETIME(6) NULL,
                                    invalidated_at DATETIME(6) NULL,

                                    PRIMARY KEY (id),

                                    CONSTRAINT uk_verification_grant_challenge
                                        UNIQUE (challenge_id),

                                    CONSTRAINT uk_verification_grant_hash
                                        UNIQUE (grant_hash),

                                    CONSTRAINT fk_verification_grant_challenge
                                        FOREIGN KEY (challenge_id)
                                            REFERENCES verification_challenge (id)
);