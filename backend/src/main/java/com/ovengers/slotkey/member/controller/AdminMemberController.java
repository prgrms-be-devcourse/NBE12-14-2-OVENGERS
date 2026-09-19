package com.ovengers.slotkey.member.controller;

import com.ovengers.slotkey.global.common.response.ApiResponse;
import com.ovengers.slotkey.global.common.response.PageResponse;
import com.ovengers.slotkey.global.security.AuthPrincipal;
import com.ovengers.slotkey.member.authorization.MemberAuthorizationService;
import com.ovengers.slotkey.member.dto.request.MemberCreditGrantRequest;
import com.ovengers.slotkey.member.dto.request.MemberStatusChangeRequest;
import com.ovengers.slotkey.member.dto.response.AdminMemberResponse;
import com.ovengers.slotkey.member.dto.response.MemberStatusChangeResponse;
import com.ovengers.slotkey.member.entity.MemberStatus;
import com.ovengers.slotkey.member.service.AdminMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {

    private final AdminMemberService adminMemberService;
    private final MemberAuthorizationService memberAuthorizationService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminMemberResponse>>> getMembers(
            @RequestParam(required = false) MemberStatus status,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal AuthPrincipal authPrincipal) {
        memberAuthorizationService.validateCanManageMember(authPrincipal);
        PageResponse<AdminMemberResponse> response =
                PageResponse.from(adminMemberService.getMembers(status, keyword, pageable));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{memberId}/suspend")
    public ResponseEntity<ApiResponse<MemberStatusChangeResponse>> suspend(
            @PathVariable Long memberId,
            @Valid @RequestBody MemberStatusChangeRequest request,
            @AuthenticationPrincipal AuthPrincipal authPrincipal) {
        memberAuthorizationService.validateCanManageMember(authPrincipal);
        MemberStatusChangeResponse response =
                adminMemberService.suspend(memberId, request.reason(), authPrincipal.memberId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{memberId}/restore")
    public ResponseEntity<ApiResponse<MemberStatusChangeResponse>> restore(
            @PathVariable Long memberId,
            @Valid @RequestBody MemberStatusChangeRequest request,
            @AuthenticationPrincipal AuthPrincipal authPrincipal) {
        memberAuthorizationService.validateCanManageMember(authPrincipal);
        MemberStatusChangeResponse response =
                adminMemberService.restore(memberId, request.reason(), authPrincipal.memberId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{memberId}/credits")
    public ResponseEntity<ApiResponse<AdminMemberResponse>> grantCredit(
            @PathVariable Long memberId,
            @Valid @RequestBody MemberCreditGrantRequest request,
            @AuthenticationPrincipal AuthPrincipal authPrincipal) {
        memberAuthorizationService.validateCanManageMember(authPrincipal);
        AdminMemberResponse response = adminMemberService.grantCredit(
                memberId, request.amount(), request.reason(), authPrincipal.memberId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
