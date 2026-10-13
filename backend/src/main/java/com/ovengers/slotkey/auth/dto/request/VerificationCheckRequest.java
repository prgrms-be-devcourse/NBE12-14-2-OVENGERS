package com.ovengers.slotkey.auth.dto.request;

import com.ovengers.slotkey.auth.entity.VerificationPurpose;
import jakarta.validation.constraints.*;

public record VerificationCheckRequest(
        @NotBlank
        @Email
        @Size(max = 254)
        String email,

        @NotNull
        VerificationPurpose purpose,

        @NotNull
        @Positive
        Long challengeId,

        @NotBlank
        @Pattern(
                regexp = "[0-9]{8}",
                message = "인증번호는 8자리 숫자여야 합니다."
        )
        String code
) {
}