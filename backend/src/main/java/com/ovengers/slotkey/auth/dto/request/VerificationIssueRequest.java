package com.ovengers.slotkey.auth.dto.request;

import com.ovengers.slotkey.auth.entity.VerificationPurpose;
import jakarta.validation.constraints.*;

public record VerificationIssueRequest(
        @NotBlank
        @Email
        @Size(max = 254)
        String email,

        @NotNull
        VerificationPurpose purpose
) {
}