package com.ovengers.slotkey.credit.service;

import com.ovengers.slotkey.credit.entity.CreditTransaction;
import com.ovengers.slotkey.credit.entity.CreditTransactionType;
import com.ovengers.slotkey.credit.repository.CreditTransactionRepository;
import com.ovengers.slotkey.credit.repository.CreditBalanceRepository;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.reservation.entity.Reservation;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreditServiceImpl implements CreditService {

    private final CreditTransactionRepository creditTransactionRepository;
    private final CreditBalanceRepository creditBalanceRepository;
    private final EntityManager entityManager;
    private final Clock clock;

    // 예약 결제 크레딧 차감
    @Override
    @Transactional
    public int charge(
            Long memberId,
            Long reservationId,
            int amount
    ) {
        validateAmount(amount);

        int updatedRows =
                creditBalanceRepository.decreaseIfEnough(memberId, amount);

        if (updatedRows == 0) {
            throw new BusinessException(
                    ErrorCode.INSUFFICIENT_BALANCE
            );
        }

        int balanceAfter =
                creditBalanceRepository.findBalance(memberId);

        saveTransaction(
                memberId,
                reservationId,
                -amount,
                CreditTransactionType.RESERVATION_CHARGE,
                balanceAfter
        );

        return balanceAfter;
    }

    // 예약 취소 크레딧 환급
    @Override
    @Transactional
    public int refund(
            Long memberId,
            Long reservationId,
            int amount
    ) {
        validateAmount(amount);

        creditBalanceRepository.increase(memberId, amount);

        int balanceAfter =
                creditBalanceRepository.findBalance(memberId);

        saveTransaction(
                memberId,
                reservationId,
                amount,
                CreditTransactionType.REFUND,
                balanceAfter
        );

        return balanceAfter;
    }

    // 예약 취소 위약금 차감
    @Override
    @Transactional
    public int penalize(
            Long memberId,
            Long reservationId,
            int amount
    ) {
        validateAmount(amount);

        int updatedRows =
                creditBalanceRepository.decreaseIfEnough(memberId, amount);

        if (updatedRows == 0) {
            throw new BusinessException(
                    ErrorCode.INSUFFICIENT_BALANCE
            );
        }

        int balanceAfter =
                creditBalanceRepository.findBalance(memberId);

        saveTransaction(
                memberId,
                reservationId,
                -amount,
                CreditTransactionType.PENALTY,
                balanceAfter
        );

        return balanceAfter;
    }

    // 크레딧 거래 이력 저장
    private void saveTransaction(
            Long memberId,
            Long reservationId,
            int amount,
            CreditTransactionType type,
            int balanceAfter
    ) {
        Member member =
                entityManager.getReference(Member.class, memberId);

        Reservation reservation =
                entityManager.getReference(Reservation.class, reservationId);

        CreditTransaction transaction = new CreditTransaction(
                member,
                amount,
                type,
                reservation,
                balanceAfter,
                null,
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
}