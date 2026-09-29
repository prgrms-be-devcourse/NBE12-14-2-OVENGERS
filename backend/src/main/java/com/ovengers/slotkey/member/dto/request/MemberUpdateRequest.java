package com.ovengers.slotkey.member.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "내 닉네임 수정 요청")
public record MemberUpdateRequest(

        @Schema(description = "닉네임 (2~50자)", example = "새닉네임")
        @NotBlank(message = "닉네임을 입력해주세요.")
        @Size(min = 2, max = 50, message = "닉네임은 2자 이상 50자 이하여야 합니다.")
        String nickname

) {
    public MemberUpdateRequest {
        if (nickname != null) {
            nickname = nickname.strip();
        }
    }
}
