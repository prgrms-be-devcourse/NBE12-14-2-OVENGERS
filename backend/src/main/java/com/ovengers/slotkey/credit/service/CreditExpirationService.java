package com.ovengers.slotkey.credit.service;

import com.ovengers.slotkey.credit.entity.CreditTransaction;
import com.ovengers.slotkey.credit.entity.CreditTransactionType;
import com.ovengers.slotkey.credit.repository.CreditTransactionRepository;
import com.ovengers.slotkey.member.entity.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CreditExpirationService {

    private final CreditTransactionRepository creditTransactionRepository;

    /**
     * 탈퇴 트랜잭션에서 호출한다.
     * member는 해당 트랜잭션에서 잠금 조회한 관리 상태의 엔티티여야 한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int expireForWithdrawal(Member member, LocalDateTime now) {
        if (member.getBalance() < 0) {
            throw new IllegalStateException(
                    "회원 크레딧 잔액은 음수일 수 없습니다."
            );
        }

        int expiredAmount = member.expireRemainingCredit();

        if (expiredAmount == 0) {
            return 0;
        }

        CreditTransaction transaction = new CreditTransaction(
                member,
                -expiredAmount,
                CreditTransactionType.WITHDRAWAL_EXPIRATION,
                null,
                0,
                "회원탈퇴로 인한 잔여 크레딧 소멸",
                now
        );

        creditTransactionRepository.save(transaction);

        return expiredAmount;
    }
}