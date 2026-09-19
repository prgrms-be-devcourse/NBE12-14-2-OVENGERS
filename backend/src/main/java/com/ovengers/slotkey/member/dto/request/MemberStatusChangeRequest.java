package com.ovengers.slotkey.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MemberStatusChangeRequest(
        @NotBlank(message = "사유를 입력해주세요.")
        @Size(min = 1, max = 500, message = "사유는 1~500자여야 합니다.")
        String reason
) {
}
