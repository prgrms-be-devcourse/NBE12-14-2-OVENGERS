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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;

@Tag(name = "관리자 - 회원", description = "회원 조회, 정지·복구 및 크레딧 지급 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {

    private final AdminMemberService adminMemberService;
    private final MemberAuthorizationService memberAuthorizationService;

    @Operation(
            summary = "회원 목록 조회",
            description = """
                관리자 권한으로 회원 목록을 조회합니다.
                status로 회원 상태를 필터링하고, keyword로 검색할 수 있습니다.
                page는 0부터 시작하며, 기본 페이지 크기는 20입니다.
                """
    )
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminMemberResponse>>> getMembers(
            @RequestParam(required = false) MemberStatus status,
            @RequestParam(required = false) String keyword,
            @ParameterObject
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal AuthPrincipal authPrincipal) {
        memberAuthorizationService.validateCanManageMember(authPrincipal);
        PageResponse<AdminMemberResponse> response =
                PageResponse.from(adminMemberService.getMembers(status, keyword, pageable));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
            summary = "회원 정지",
            description = """
                관리자 권한으로 대상 회원을 정지합니다.
                요청 본문에 정지 사유를 입력해야 합니다.
                """
    )
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


    @Operation(
            summary = "회원 복구",
            description = """
                관리자 권한으로 대상 회원의 상태를 복구합니다.
                요청 본문에 복구 사유를 입력해야 합니다.
                """
    )
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

    @Operation(
            summary = "회원 크레딧 지급",
            description = """
                관리자 권한으로 대상 회원에게 크레딧을 지급합니다.
                요청 본문에 지급 금액과 사유를 입력해야 합니다.
                처리 후 대상 회원 정보를 반환합니다.
                """
    )
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
