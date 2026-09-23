package com.ovengers.slotkey.inquiry.service;

import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.inquiry.authorization.InquiryAuthorizationService;
import com.ovengers.slotkey.inquiry.dto.request.InquiryCreateRequest;
import com.ovengers.slotkey.inquiry.dto.request.InquiryUpdateRequest;
import com.ovengers.slotkey.inquiry.entity.Inquiry;
import com.ovengers.slotkey.inquiry.repository.InquiryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원용 문의 API. 본인 문의만 조회·수정할 수 있다 — 관리자 조회/답변은 AdminInquiryService가 담당한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InquiryService {

    private final InquiryRepository inquiryRepository;
    private final InquiryAuthorizationService inquiryAuthorizationService;

    @Transactional
    public Inquiry create(Long memberId, InquiryCreateRequest request) {
        Inquiry inquiry = Inquiry.create(memberId, request.title(), request.content());
        return inquiryRepository.save(inquiry);
    }

    public Page<Inquiry> getMyInquiries(Long memberId, Pageable pageable) {
        return inquiryRepository.findAllByMemberId(memberId, pageable);
    }

    public Inquiry getMyInquiry(Long memberId, Long inquiryId) {
        Inquiry inquiry = getOrThrow(inquiryId);
        inquiryAuthorizationService.validateOwner(memberId, inquiry.getMemberId());
        return inquiry;
    }

    @Transactional
    public Inquiry update(Long memberId, Long inquiryId, InquiryUpdateRequest request) {
        Inquiry inquiry = getOrThrow(inquiryId);
        inquiryAuthorizationService.validateOwner(memberId, inquiry.getMemberId());
        inquiry.updateContent(request.title(), request.content());
        return inquiry;
    }

    private Inquiry getOrThrow(Long inquiryId) {
        return inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INQUIRY_NOT_FOUND));
    }
}
