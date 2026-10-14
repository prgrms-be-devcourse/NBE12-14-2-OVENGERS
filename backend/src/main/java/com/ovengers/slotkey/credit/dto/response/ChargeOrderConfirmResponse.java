package com.ovengers.slotkey.credit.dto.response;

import com.ovengers.slotkey.credit.entity.ChargeOrder;
import com.ovengers.slotkey.credit.entity.ChargeOrderStatus;

public record ChargeOrderConfirmResponse(
        String orderId,
        long amount,
        ChargeOrderStatus status
) {
    public static ChargeOrderConfirmResponse from(ChargeOrder chargeOrder) {
        return new ChargeOrderConfirmResponse(
                chargeOrder.getOrderId(),
                chargeOrder.getAmount(),
                chargeOrder.getStatus()
        );
    }
}
