ALTER TABLE member
    ADD COLUMN email_verified_at DATETIME(6) NULL;

-- 이번 마이그레이션 적용 시 존재하는 회원을 정책상 인증 완료로 인정한다.
-- 실제 이메일 소유 검증 시각이 아니라 정책 적용 시각이다.
UPDATE member
SET email_verified_at = CURRENT_TIMESTAMP(6);