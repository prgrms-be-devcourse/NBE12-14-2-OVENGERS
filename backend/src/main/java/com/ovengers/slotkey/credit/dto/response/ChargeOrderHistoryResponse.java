package com.ovengers.slotkey.credit.dto.response;

import com.ovengers.slotkey.credit.entity.ChargeOrder;
import com.ovengers.slotkey.credit.entity.ChargeOrderStatus;

import java.time.LocalDateTime;

/** 내 충전(PG 결제) 내역 한 줄. paymentMethod·paymentProvider는 토스가 알려 준 표시용 값이며 없을 수 있다(NULL). updatedAt은 승인·실패 등 마지막 상태 변경 시각이다. */
public record ChargeOrderHistoryResponse(
        String orderId,
        long amount,
        ChargeOrderStatus status,
        String paymentMethod,
        String paymentProvider,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ChargeOrderHistoryResponse from(ChargeOrder order) {
        return new ChargeOrderHistoryResponse(
                order.getOrderId(),
                order.getAmount(),
                order.getStatus(),
                order.getPaymentMethod(),
                order.getPaymentProvider(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
