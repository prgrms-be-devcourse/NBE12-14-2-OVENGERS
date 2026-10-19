-- V1 refresh tokens have no session identity. In this one-time transition they
-- are discarded; future consumed-token history is retained by V2 rotation.
DELETE FROM refresh_token;

CREATE TABLE auth_session (
    id VARCHAR(36) NOT NULL,
    member_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    INDEX idx_auth_session_member_id (member_id),
    CONSTRAINT fk_auth_session_member
        FOREIGN KEY (member_id) REFERENCES member (id)
);

ALTER TABLE refresh_token
    ADD COLUMN auth_session_id VARCHAR(36) NOT NULL,
    ADD INDEX idx_refresh_token_auth_session_id (auth_session_id),
    ADD CONSTRAINT fk_refresh_token_auth_session
        FOREIGN KEY (auth_session_id) REFERENCES auth_session (id);
