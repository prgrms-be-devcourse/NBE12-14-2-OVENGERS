package com.ovengers.slotkey.inquiry.entity;

import com.ovengers.slotkey.global.common.entity.BaseTimeEntity;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 회원 문의(Q&A) + 관리자 답변. 문의 1건당 답변 1건(1:1)으로 단순화한다 — 후속 재질문/재답변
 * 스레드는 범위 밖(2026-09-23, MVP 3대 기능 외 추가 기능). WAITING 상태에서만 작성자가 제목/내용을
 * 수정할 수 있고, 답변이 등록되면(ANSWERED) 잠긴다.
 */
@Entity
@Table(name = "inquiries")
@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Inquiry extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId; // 작성자(문의한 회원)

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InquiryStatus status;

    @Column(name = "answer_content", columnDefinition = "TEXT")
    private String answerContent;

    @Column(name = "answered_by_member_id")
    private Long answeredByMemberId; // 답변한 관리자

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    public static Inquiry create(Long memberId, String title, String content) {
        return Inquiry.builder()
                .memberId(memberId)
                .title(title)
                .content(content)
                .status(InquiryStatus.WAITING)
                .build();
    }

    /** WAITING 상태에서만 수정 가능. 답변이 이미 달린 문의는 잠긴다. */
    public void updateContent(String title, String content) {
        if (this.status != InquiryStatus.WAITING) {
            throw new BusinessException(ErrorCode.INQUIRY_ALREADY_ANSWERED);
        }
        this.title = title;
        this.content = content;
    }

    public void answer(Long adminMemberId, String answerContent, LocalDateTime now) {
        this.answerContent = answerContent;
        this.answeredByMemberId = adminMemberId;
        this.answeredAt = now;
        this.status = InquiryStatus.ANSWERED;
    }
}
