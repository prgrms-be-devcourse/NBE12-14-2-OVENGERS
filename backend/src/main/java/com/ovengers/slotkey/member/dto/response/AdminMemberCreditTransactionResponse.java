package com.ovengers.slotkey.member.dto.response;


import com.ovengers.slotkey.credit.entity.CreditTransaction;
import com.ovengers.slotkey.credit.entity.CreditTransactionType;

import java.time.LocalDateTime;

public record AdminMemberCreditTransactionResponse(
        Long transactionId,
        CreditTransactionType type,
        int amount,
        int balanceAfter,
        String reason,
        LocalDateTime createdAt
) {
    public static AdminMemberCreditTransactionResponse from(
            CreditTransaction transaction
    ) {
        return new AdminMemberCreditTransactionResponse(
                transaction.getId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                transaction.getReason(),
                transaction.getCreatedAt()
        );
    }
}