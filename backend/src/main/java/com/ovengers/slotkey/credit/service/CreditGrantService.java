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

        // 회원 잔액 증가
        int updatedRows =
                creditBalanceRepository.increase(memberId, amount);

        // 해당 회원이 없어서 UPDATE되지 않은 경우
        if (updatedRows == 0) {
            throw new BusinessException(
                    ErrorCode.MEMBER_NOT_FOUND
            );
        }

        // 크레딧 지급 후 회원 정보 조회
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_NOT_FOUND
                ));

        // 회원가입 크레딧 지급 내역 생성
        CreditTransaction transaction = new CreditTransaction(
                member,
                amount,
                CreditTransactionType.SIGNUP_GRANT,
                null,
                member.getBalance(),
                null,
                LocalDateTime.now(clock)
        );

        // 크레딧 거래 내역 저장
        creditTransactionRepository.save(transaction);

        //  지급 후 잔액 반환
        return member.getBalance();
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
}