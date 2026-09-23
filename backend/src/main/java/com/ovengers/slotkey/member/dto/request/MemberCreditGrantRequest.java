package com.ovengers.slotkey.member.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "관리자 크레딧 지급 요청")
public record MemberCreditGrantRequest(

        @Schema(
                description = "지급할 크레딧 금액 (1 이상)",
                example = "10000"
        )
        @NotNull(message = "지급할 크레딧 금액을 입력해주세요.")
        @Min(value = 1, message = "지급할 크레딧 금액은 1 이상이어야 합니다.")
        Integer amount,

        @Schema(
                description = "크레딧 지급 사유 (1~500자)",
                example = "이벤트 참여 보상"
        )
        @NotBlank(message = "사유를 입력해주세요.")
        @Size(min = 1, max = 500, message = "사유는 1~500자여야 합니다.")
        String reason
) {
}