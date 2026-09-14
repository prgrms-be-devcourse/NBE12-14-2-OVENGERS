package com.ovengers.slotkey.payment.dto.response;

import com.ovengers.slotkey.payment.entity.Payment;
import com.ovengers.slotkey.payment.entity.PaymentStatus;

import java.time.LocalDateTime;

public record PaymentResponse(
        PaymentStatus status,
        LocalDateTime paidAt,
        LocalDateTime cancelledAt
) {

    // DTO 변환 메서드
    public static PaymentResponse from(Payment payment){
        return new PaymentResponse(
                payment.getStatus(),
                payment.getPaidAt(),
                payment.getCancelledAt()
        );
    }
}