# 일시: 2026-09-23
# 담당: 이태호 (관리자 Q&A 게시판 - 신규 추가 기능, 팀 엔터티 분담표 갱신 필요)
# 테이블: inquiries
# 선행 의존(FK 대상): member
# 비고: 문의 1건당 답변 1건(1:1)으로 단순화한다. 재질문/스레드형 재답변은 범위 밖
#       (MVP 3대 기능 외 추가 기능, docs/requirements.md 갱신 필요).

CREATE TABLE inquiries (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id             BIGINT NOT NULL,
    title                 VARCHAR(200) NOT NULL,
    content               TEXT NOT NULL,
    status                VARCHAR(20) NOT NULL,   -- WAITING/ANSWERED
    answer_content        TEXT NULL,
    answered_by_member_id BIGINT NULL,
    answered_at           DATETIME NULL,
    created_at            DATETIME NOT NULL,
    updated_at            DATETIME NULL,
    CONSTRAINT fk_inquiries_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_inquiries_answered_by FOREIGN KEY (answered_by_member_id) REFERENCES member (id),
    CONSTRAINT chk_inquiries_answered CHECK (
        status <> 'ANSWERED' OR (answer_content IS NOT NULL AND answered_at IS NOT NULL)
    )
) ENGINE = InnoDB;
