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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

@Tag(name = "회원", description = "회원 정보 조회 API")
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @Operation(
            summary = "내 정보 조회",
            description = "로그인한 회원의 이메일, 닉네임, 역할, 크레딧 잔액 등을 조회합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "내 정보 조회 성공",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = """
                        AUTHENTICATION_REQUIRED: 인증 정보가 없거나 회원이 존재하지 않음.
                        INVALID_ACCESS_TOKEN: 액세스 토큰이 만료되었거나 유효하지 않음.
                        """,
                    content = @Content
            )
    })
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