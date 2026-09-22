package com.ovengers.slotkey.credit.service;

import com.ovengers.slotkey.credit.entity.CreditTransaction;
import com.ovengers.slotkey.credit.entity.CreditTransactionType;
import com.ovengers.slotkey.credit.repository.CreditBalanceRepository;
import com.ovengers.slotkey.credit.repository.CreditTransactionRepository;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CreditGrantService {

    private final CreditBalanceRepository creditBalanceRepository;
    private final CreditTransactionRepository creditTransactionRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;

    // 회원가입 크레딧 지급
    @Transactional
    public int grantSignupCredit(
            Long memberId,
            int amount
    ) {
        // 지급 금액 검증
        validateAmount(amount);

        // 지급 대상 회원 조회
        Member member = findMember(memberId);

        // 지급 후 잔액 검증
        validateBalanceAfterGrant(
                member.getBalance(),
                amount
        );

        // 회원 잔액 증가
        increaseBalance(memberId, amount);

        // 증가된 잔액을 가진 회원 조회
        Member updatedMember = findMember(memberId);

        // 회원가입 크레딧 지급 내역 저장
        saveGrantTransaction(
                updatedMember,
                amount,
                CreditTransactionType.SIGNUP_GRANT,
                null
        );

        // 지급 후 잔액 반환
        return updatedMember.getBalance();
    }

    // 관리자 크레딧 지급
    @Transactional
    public int grantAdminCredit(
            Long memberId,
            int amount,
            String reason
    ) {
        // 지급 금액 검증
        validateAmount(amount);

        // 관리자 지급 사유 검증
        validateReason(reason);

        // 지급 대상 회원 조회
        Member member = findMember(memberId);

        // 지급 후 잔액 검증
        validateBalanceAfterGrant(
                member.getBalance(),
                amount
        );

        // 회원 잔액 증가
        increaseBalance(memberId, amount);

        // 증가된 잔액을 가진 회원 조회
        Member updatedMember = findMember(memberId);

        // 관리자 크레딧 지급 내역 저장
        saveGrantTransaction(
                updatedMember,
                amount,
                CreditTransactionType.ADMIN_GRANT,
                reason
        );

        // 지급 후 잔액 반환
        return updatedMember.getBalance();
    }

    // 회원 잔액 증가
    private void increaseBalance(
            Long memberId,
            int amount
    ) {
        int updatedRows =
                creditBalanceRepository.increase(memberId, amount);

        // 해당 회원이 없어서 UPDATE되지 않은 경우
        if (updatedRows == 0) {
            throw new BusinessException(
                    ErrorCode.MEMBER_NOT_FOUND
            );
        }
    }

    // ID로 회원 조회
    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_NOT_FOUND
                ));
    }

    // 크레딧 지급 거래 내역 저장
    private void saveGrantTransaction(
            Member member,
            int amount,
            CreditTransactionType type,
            String reason
    ) {
        CreditTransaction transaction = new CreditTransaction(
                member,
                amount,
                type,
                null,
                member.getBalance(),
                reason,
                LocalDateTime.now(clock)
        );

        creditTransactionRepository.save(transaction);
    }

    // 지급 금액 검증
    private void validateAmount(int amount) {
        if (amount <= 0) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "크레딧 금액은 0보다 커야 합니다."
            );
        }
    }

    // 지급 후 잔액 검증
    private void validateBalanceAfterGrant(
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

    // 관리자 지급 사유 검증
    private void validateReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "관리자 크레딧 지급 사유는 필수입니다."
            );
        }

        if (reason.length() > 500) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "관리자 크레딧 지급 사유는 500자 이하여야 합니다."
            );
        }
    }
}