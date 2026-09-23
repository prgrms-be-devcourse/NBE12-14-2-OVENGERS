package com.ovengers.slotkey.inquiry.dto.response;

import com.ovengers.slotkey.inquiry.entity.Inquiry;
import com.ovengers.slotkey.inquiry.entity.InquiryStatus;

import java.time.LocalDateTime;

public record InquiryResponse(
        Long id,
        Long memberId,
        String title,
        String content,
        InquiryStatus status,
        String answerContent,
        Long answeredByMemberId,
        LocalDateTime answeredAt,
        LocalDateTime createdAt
) {
    public static InquiryResponse from(Inquiry inquiry) {
        return new InquiryResponse(
                inquiry.getId(),
                inquiry.getMemberId(),
                inquiry.getTitle(),
                inquiry.getContent(),
                inquiry.getStatus(),
                inquiry.getAnswerContent(),
                inquiry.getAnsweredByMemberId(),
                inquiry.getAnsweredAt(),
                inquiry.getCreatedAt());
    }
}
