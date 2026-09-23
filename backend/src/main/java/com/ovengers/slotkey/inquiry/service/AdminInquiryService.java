package com.ovengers.slotkey.inquiry.service;

import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.inquiry.dto.request.InquiryAnswerRequest;
import com.ovengers.slotkey.inquiry.entity.Inquiry;
import com.ovengers.slotkey.inquiry.entity.InquiryStatus;
import com.ovengers.slotkey.inquiry.repository.InquiryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/** 관리자용 문의 조회/답변. 접근 제한은 SecurityConfig의 "/api/v1/admin/**" -> hasRole("ADMIN")로 처리된다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminInquiryService {

    private final InquiryRepository inquiryRepository;
    private final Clock clock;

    public Page<Inquiry> getInquiries(InquiryStatus status, Pageable pageable) {
        return status == null
                ? inquiryRepository.findAll(pageable)
                : inquiryRepository.findAllByStatus(status, pageable);
    }

    public Inquiry getInquiry(Long inquiryId) {
        return inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INQUIRY_NOT_FOUND));
    }

    @Transactional
    public Inquiry answer(Long inquiryId, Long adminMemberId, InquiryAnswerRequest request) {
        Inquiry inquiry = getInquiry(inquiryId);
        inquiry.answer(adminMemberId, request.content(), LocalDateTime.now(clock));
        return inquiry;
    }
}
