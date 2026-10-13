package com.ovengers.slotkey.credit.dto.response;

import com.ovengers.slotkey.credit.entity.ChargeOrder;
import com.ovengers.slotkey.credit.entity.ChargeOrderStatus;

public record ChargeOrderResponse(
        String orderId,
        long amount,
        ChargeOrderStatus status
) {
    public static ChargeOrderResponse from(ChargeOrder chargeOrder) {
        return new ChargeOrderResponse(
                chargeOrder.getOrderId(),
                chargeOrder.getAmount(),
                chargeOrder.getStatus()
        );
    }
}
