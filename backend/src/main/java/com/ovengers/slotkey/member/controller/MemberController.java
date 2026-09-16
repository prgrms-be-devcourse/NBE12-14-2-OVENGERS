package com.ovengers.slotkey.member.controller;

import com.ovengers.slotkey.global.common.response.ApiResponse;
import com.ovengers.slotkey.global.security.AuthPrincipal;
import com.ovengers.slotkey.member.dto.response.MemberResponse;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MemberResponse>> getMyInfo(
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        Member member = memberService.getMyInfo(principal.memberId());

        return ResponseEntity.ok(
                ApiResponse.success(new MemberResponse(member))
        );
    }
}