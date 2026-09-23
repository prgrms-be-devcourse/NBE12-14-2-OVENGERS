package com.ovengers.slotkey.inquiry.controller;

import com.ovengers.slotkey.global.common.response.ApiResponse;
import com.ovengers.slotkey.global.common.response.PageResponse;
import com.ovengers.slotkey.global.security.AuthPrincipal;
import com.ovengers.slotkey.global.security.CurrentMember;
import com.ovengers.slotkey.inquiry.dto.request.InquiryCreateRequest;
import com.ovengers.slotkey.inquiry.dto.request.InquiryUpdateRequest;
import com.ovengers.slotkey.inquiry.dto.response.InquiryResponse;
import com.ovengers.slotkey.inquiry.entity.Inquiry;
import com.ovengers.slotkey.inquiry.service.InquiryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 본인 문의(Q&A) API. SecurityConfig에서 이 경로는 anyRequest().authenticated()로 이미
 * 인증을 강제하므로, 여기서는 소유권 검사만 신경 쓰면 된다(서비스가 FORBIDDEN_NOT_OWNER로 처리).
 */
@RestController
@RequestMapping("/api/v1/inquiries")
@RequiredArgsConstructor
public class InquiryController {

    private final InquiryService inquiryService;

    @PostMapping
    public ResponseEntity<ApiResponse<InquiryResponse>> create(
            @CurrentMember AuthPrincipal principal,
            @Valid @RequestBody InquiryCreateRequest request) {
        Inquiry inquiry = inquiryService.create(principal.memberId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(InquiryResponse.from(inquiry)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<InquiryResponse>>> getMyInquiries(
            @CurrentMember AuthPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<InquiryResponse> page = inquiryService.getMyInquiries(principal.memberId(), pageable)
                .map(InquiryResponse::from);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(page)));
    }

    @GetMapping("/{inquiryId}")
    public ResponseEntity<ApiResponse<InquiryResponse>> getMyInquiry(
            @CurrentMember AuthPrincipal principal,
            @PathVariable Long inquiryId) {
        Inquiry inquiry = inquiryService.getMyInquiry(principal.memberId(), inquiryId);
        return ResponseEntity.ok(ApiResponse.success(InquiryResponse.from(inquiry)));
    }

    @PatchMapping("/{inquiryId}")
    public ResponseEntity<ApiResponse<InquiryResponse>> update(
            @CurrentMember AuthPrincipal principal,
            @PathVariable Long inquiryId,
            @Valid @RequestBody InquiryUpdateRequest request) {
        Inquiry inquiry = inquiryService.update(principal.memberId(), inquiryId, request);
        return ResponseEntity.ok(ApiResponse.success(InquiryResponse.from(inquiry)));
    }
}
