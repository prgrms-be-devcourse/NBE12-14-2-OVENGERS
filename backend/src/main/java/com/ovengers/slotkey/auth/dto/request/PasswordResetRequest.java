package com.ovengers.slotkey.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;

@Schema(description = "비밀번호 재설정 요청")
public record PasswordResetRequest(
        @Schema(description = "등록된 이메일")
        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 254, message = "이메일은 254자 이하여야 합니다.")
        String email,

        @Schema(description = "비밀번호 재설정 인증 증표")
        @NotBlank(message = "이메일 인증 증표를 전달해주세요.")
        @Size(max = 128, message = "인증 증표 형식이 올바르지 않습니다.")
        String grant,

        @Schema(description = "새 비밀번호")
        @NotBlank(message = "새 비밀번호를 입력해주세요.")
        String password,

        @Schema(description = "새 비밀번호 확인값")
        @NotBlank(message = "새 비밀번호 확인을 입력해주세요.")
        String passwordConfirm
) {
}
