-- 논리적 회원탈퇴 시각을 보존한다.
ALTER TABLE member
    ADD COLUMN withdrawn_at DATETIME(6) NULL;
