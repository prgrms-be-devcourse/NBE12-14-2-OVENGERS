-- 로컬 비밀번호 인증 정보를 회원 정보에서 분리
CREATE TABLE local_credential (
                                  member_id BIGINT NOT NULL,
                                  password_hash VARCHAR(255) NOT NULL,
                                  password_changed_at DATETIME(6) NOT NULL,

                                  PRIMARY KEY (member_id),

                                  CONSTRAINT fk_local_credential_member
                                      FOREIGN KEY (member_id)
                                          REFERENCES member (id)
);

-- 기존 해시를 재해싱하지 않고 그대로 이전
-- 과거 비밀번호 변경 시각을 알 수 없어 이전 시각으로 기록
INSERT INTO local_credential (
    member_id,
    password_hash,
    password_changed_at
)
SELECT
    id,
    password_hash,
    CURRENT_TIMESTAMP(6)
FROM member;

-- 이전 완료 후 기존 비밀번호 컬럼 제거
ALTER TABLE member
DROP COLUMN password_hash;