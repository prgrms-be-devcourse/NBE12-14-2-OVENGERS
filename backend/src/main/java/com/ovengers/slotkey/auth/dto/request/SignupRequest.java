package com.ovengers.slotkey.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "회원가입 요청")
public record SignupRequest(
        @Schema(description = "가입할 이메일", example = "swagger.user@example.com")
        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 254, message = "이메일은 254자 이하여야 합니다.")
        String email,

        @Schema(description = "비밀번호", example = "Sample1234!")
        @NotBlank(message = "비밀번호를 입력해주세요.")
        String password,

        @Schema(description = "비밀번호와 동일한 확인값", example = "Sample1234!")
        @NotBlank(message = "비밀번호 확인을 입력해주세요.")
        String passwordConfirm,

        @Schema(description = "닉네임 (2~50자)", example = "슬롯유저")
        @NotBlank(message = "닉네임을 입력해주세요.")
        @Size(min = 2, max = 50, message = "닉네임은 2자 이상 50자 이하여야 합니다.")
        String nickname
) {
}
