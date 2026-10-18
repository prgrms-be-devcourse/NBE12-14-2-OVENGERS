CREATE TABLE login_attempt_bucket (
    email VARCHAR(254) NOT NULL,
    window_id BIGINT NOT NULL,
    attempt_count SMALLINT UNSIGNED NOT NULL DEFAULT 0,

    PRIMARY KEY (email, window_id),
    CONSTRAINT chk_login_attempt_bucket_count
        CHECK (attempt_count BETWEEN 0 AND 10)
);
