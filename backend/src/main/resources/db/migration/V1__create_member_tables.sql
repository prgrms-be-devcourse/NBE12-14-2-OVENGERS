-- 회원 테이블
CREATE TABLE member (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        email VARCHAR(254) NOT NULL,
                        password_hash VARCHAR(255) NOT NULL,
                        nickname VARCHAR(50) NOT NULL,
                        role VARCHAR(20) NOT NULL,
                        status VARCHAR(20) NOT NULL,
                        balance INT NOT NULL DEFAULT 0,
                        created_at DATETIME(6) NOT NULL,

                        PRIMARY KEY (id),
                        UNIQUE (email)
);

-- 리프레시 토큰 테이블
CREATE TABLE refresh_token (
                               id BIGINT NOT NULL AUTO_INCREMENT,
                               member_id BIGINT NOT NULL,
                               token_hash VARCHAR(64) NOT NULL,
                               expires_at DATETIME(6) NOT NULL,
                               revoked_at DATETIME(6) NULL,

                               PRIMARY KEY (id),
                               UNIQUE (token_hash),

                               CONSTRAINT fk_refresh_token_member
                                   FOREIGN KEY (member_id)
                                       REFERENCES member (id)
);