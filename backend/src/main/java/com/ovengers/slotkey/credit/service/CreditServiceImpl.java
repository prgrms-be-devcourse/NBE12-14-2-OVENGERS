package com.ovengers.slotkey.credit.service;

import com.ovengers.slotkey.credit.entity.CreditTransaction;
import com.ovengers.slotkey.credit.entity.CreditTransactionType;
import com.ovengers.slotkey.credit.repository.CreditTransactionRepository;
import com.ovengers.slotkey.credit.repository.CreditBalanceRepository;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.repository.MemberRepository;
import com.ovengers.slotkey.reservation.entity.Reservation;
import com.ovengers.slotkey.reservation.repository.ReservationRepository;
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
    private final MemberRepository memberRepository;
    private final ReservationRepository reservationRepository;
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

        // 잔액이 충분한 경우에만 차감
        int updatedRows =
                creditBalanceRepository.decreaseIfEnough(memberId, amount);

        if (updatedRows == 0) {
            throw new BusinessException(
                    ErrorCode.INSUFFICIENT_BALANCE
            );
        }

        // ID로 회원 / 예약 조회
        Member member = findMember(memberId);
        Reservation reservation = findReservation(reservationId);

        saveTransaction(
                member,
                -amount,
                CreditTransactionType.RESERVATION_CHARGE,
                reservation
        );

        return member.getBalance();
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

        // 환불 전 회원 조회
        Member member = findMember(memberId);

        // 환불 후 잔액 검증
        validateBalanceAfterRefund(
                member.getBalance(),
                amount
        );

        // 잔액 증가
        int updatedRows =
                creditBalanceRepository.increase(memberId, amount);

        if (updatedRows == 0) {
            throw new BusinessException(
                    ErrorCode.MEMBER_NOT_FOUND
            );
        }

        // 증가된 잔액을 가진 회원 / 예약 조회
        Member updatedMember = findMember(memberId);
        Reservation reservation = findReservation(reservationId);

        saveTransaction(
                updatedMember,
                amount,
                CreditTransactionType.REFUND,
                reservation
        );

        return updatedMember.getBalance();
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

        // 잔액이 충분한 경우에만 차감
        int updatedRows =
                creditBalanceRepository.decreaseIfEnough(memberId, amount);

        if (updatedRows == 0) {
            throw new BusinessException(
                    ErrorCode.INSUFFICIENT_BALANCE
            );
        }

        // ID로 회원 / 예약 조회
        Member member = findMember(memberId);
        Reservation reservation = findReservation(reservationId);

        saveTransaction(
                member,
                -amount,
                CreditTransactionType.PENALTY,
                reservation
        );

        return member.getBalance();
    }

    // 크레딧 거래 이력 저장
    private void saveTransaction(
            Member member,
            int amount,
            CreditTransactionType type,
            Reservation reservation
    ) {
        CreditTransaction transaction = new CreditTransaction(
                member,
                amount,
                type,
                reservation,
                member.getBalance(),
                null,
                LocalDateTime.now(clock)
        );

        creditTransactionRepository.save(transaction);
    }

    // ID로 회원 조회
    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_NOT_FOUND
                ));
    }

    // ID로 예약 조회
    private Reservation findReservation(Long reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESERVATION_NOT_FOUND
                ));
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

    // 환불 후 잔액 검증
    private void validateBalanceAfterRefund(
            int currentBalance,
            int amount
    ) {
        long balanceAfter =
                (long) currentBalance + amount;

        if (balanceAfter > Integer.MAX_VALUE) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "크레딧 잔액이 허용 범위를 초과합니다."
            );
        }
    }
}