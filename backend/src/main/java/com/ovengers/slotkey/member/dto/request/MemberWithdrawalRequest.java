package com.ovengers.slotkey.member.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "회원탈퇴 요청")
public record MemberWithdrawalRequest(

        @Schema(description = "현재 비밀번호")
        @NotBlank(message = "현재 비밀번호를 입력해주세요.")
        String currentPassword

) {
}