package com.ovengers.slotkey.credit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ChargeOrderConfirmRequest(
        @NotBlank(message = "paymentKey는 필수입니다.")
        String paymentKey,
        @NotBlank(message = "orderId는 필수입니다.")
        String orderId,
        @NotNull(message = "충전 금액은 필수입니다.")
        Long amount
) {
}
