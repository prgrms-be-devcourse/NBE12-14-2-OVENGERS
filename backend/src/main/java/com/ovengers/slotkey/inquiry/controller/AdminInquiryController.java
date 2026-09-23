package com.ovengers.slotkey.inquiry.controller;

import com.ovengers.slotkey.global.common.response.ApiResponse;
import com.ovengers.slotkey.global.common.response.PageResponse;
import com.ovengers.slotkey.global.security.AuthPrincipal;
import com.ovengers.slotkey.global.security.CurrentMember;
import com.ovengers.slotkey.inquiry.dto.request.InquiryAnswerRequest;
import com.ovengers.slotkey.inquiry.dto.response.InquiryResponse;
import com.ovengers.slotkey.inquiry.entity.Inquiry;
import com.ovengers.slotkey.inquiry.entity.InquiryStatus;
import com.ovengers.slotkey.inquiry.service.AdminInquiryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자용 문의 조회/답변 API. SecurityConfig의 "/api/v1/admin/**" -> hasRole("ADMIN")
 * 매처로 접근이 이미 제한되므로 이 컨트롤러는 별도 역할 검사를 하지 않는다(AdminReservationController와 동일 패턴).
 */
@RestController
@RequestMapping("/api/v1/admin/inquiries")
@RequiredArgsConstructor
public class AdminInquiryController {

    private final AdminInquiryService adminInquiryService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<InquiryResponse>>> getInquiries(
            @RequestParam(required = false) InquiryStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<InquiryResponse> page = adminInquiryService.getInquiries(status, pageable)
                .map(InquiryResponse::from);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(page)));
    }

    @GetMapping("/{inquiryId}")
    public ResponseEntity<ApiResponse<InquiryResponse>> getInquiry(@PathVariable Long inquiryId) {
        Inquiry inquiry = adminInquiryService.getInquiry(inquiryId);
        return ResponseEntity.ok(ApiResponse.success(InquiryResponse.from(inquiry)));
    }

    @PostMapping("/{inquiryId}/answer")
    public ResponseEntity<ApiResponse<InquiryResponse>> answer(
            @CurrentMember AuthPrincipal principal,
            @PathVariable Long inquiryId,
            @Valid @RequestBody InquiryAnswerRequest request) {
        Inquiry inquiry = adminInquiryService.answer(inquiryId, principal.memberId(), request);
        return ResponseEntity.ok(ApiResponse.success(InquiryResponse.from(inquiry)));
    }
}
