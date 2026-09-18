package com.ovengers.slotkey.member.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MemberCreditGrantRequest(
        @NotNull(message = "지급할 크레딧 금액을 입력해주세요.")
        @Min(value = 1, message = "지급할 크레딧 금액은 1 이상이어야 합니다.")
        Integer amount,

        @NotBlank(message = "사유를 입력해주세요.")
        @Size(min = 1, max = 500, message = "사유는 1~500자여야 합니다.")
        String reason
) {
}
