package com.ovengers.slotkey.credit.dto.request;

import jakarta.validation.constraints.NotNull;

public record ChargeOrderCreateRequest(
        @NotNull(message = "충전 금액은 필수입니다.")
        Long amount
) {
}
