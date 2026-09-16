package com.ovengers.slotkey.credit.service;

import com.ovengers.slotkey.credit.entity.CreditTransaction;
import com.ovengers.slotkey.credit.entity.CreditTransactionType;
import com.ovengers.slotkey.credit.repository.CreditTransactionRepository;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.reservation.entity.Reservation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreditService {

    private final CreditTransactionRepository creditTransactionRepository;
    private final Clock clock;

    // 예약 결제 크레딧 차감
    @Transactional
    public void charge(
            Member member,
            Reservation reservation,
            int amount
    ) {
        validateAmount(amount);
        validateBalance(member, amount);

        member.decreaseBalance(amount);

        saveTransaction(
                member,
                -amount,
                CreditTransactionType.RESERVATION_CHARGE,
                reservation,
                null
        );
    }

    // 예약 취소 크레딧 환급
    @Transactional
    public void refund(
            Member member,
            Reservation reservation,
            int amount
    ) {
        validateAmount(amount);

        member.increaseBalance(amount);

        saveTransaction(
                member,
                amount,
                CreditTransactionType.REFUND,
                reservation,
                null
        );
    }

    // 크레딧 거래 이력 저장
    private void saveTransaction(
            Member member,
            int amount,
            CreditTransactionType type,
            Reservation reservation,
            String reason
    ) {
        CreditTransaction transaction = new CreditTransaction(
                member,
                amount,
                type,
                reservation,
                member.getBalance(),
                reason,
                LocalDateTime.now(clock)
        );

        creditTransactionRepository.save(transaction);
    }

    // 크레딧 금액은 0보다 큰 값만 허용
    private void validateAmount(int amount) {
        if (amount <= 0) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "크레딧 금액은 0보다 커야 합니다."
            );
        }
    }

    // 결제 금액보다 회원의 크레딧 잔액이 적으면 예외 발생
    private void validateBalance(Member member, int amount) {
        if (member.getBalance() < amount) {
            throw new BusinessException(
                    ErrorCode.INSUFFICIENT_BALANCE
            );
        }
    }
}