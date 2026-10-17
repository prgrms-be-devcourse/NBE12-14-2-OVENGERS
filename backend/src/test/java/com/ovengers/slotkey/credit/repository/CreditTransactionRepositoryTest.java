
package com.ovengers.slotkey.credit.repository;

import com.ovengers.slotkey.credit.entity.CreditTransaction;
import com.ovengers.slotkey.credit.entity.CreditTransactionType;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.repository.MemberRepository;
import com.ovengers.slotkey.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
@DisplayName("회원별 크레딧 거래 내역 Repository 통합 테스트")
class CreditTransactionRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private CreditTransactionRepository creditTransactionRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("해당 회원의 거래만 최신순으로 조회하고 페이지네이션한다")
    void findByMemberId_returnsSortedPage() {
        // given
        Member member = memberRepository.save(
                new Member("credit-test@slotkey.test", "테스트회원")
        );
        Member otherMember = memberRepository.save(
                new Member("credit-other@slotkey.test", "다른회원")
        );

        LocalDateTime earlier = LocalDateTime.of(2026, 10, 10, 10, 0);
        LocalDateTime later = LocalDateTime.of(2026, 10, 11, 10, 0);

        CreditTransaction first = creditTransactionRepository.save(
                new CreditTransaction(
                        member, 10000, CreditTransactionType.ADMIN_GRANT,
                        null, 10000, "지급", earlier
                )
        );

        CreditTransaction second = creditTransactionRepository.save(
                new CreditTransaction(
                        member, -4000, CreditTransactionType.RESERVATION_CHARGE,
                        null, 6000, "예약 사용", later
                )
        );

        CreditTransaction third = creditTransactionRepository.save(
                new CreditTransaction(
                        member, 2000, CreditTransactionType.REFUND,
                        null, 8000, "환급", later
                )
        );

        creditTransactionRepository.save(
                new CreditTransaction(
                        otherMember, 50000, CreditTransactionType.TOP_UP,
                        null, 50000, "다른 회원 충전", later
                )
        );

        PageRequest pageable = PageRequest.of(
                0, 2,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );

        // when
        Page<CreditTransaction> page0 =
                creditTransactionRepository.findByMember_Id(
                        member.getId(), pageable
                );

        Page<CreditTransaction> page1 =
                creditTransactionRepository.findByMember_Id(
                        member.getId(), pageable.next()
                );

        // then
        assertThat(page0.getTotalElements()).isEqualTo(3);
        assertThat(page0.getTotalPages()).isEqualTo(2);

        assertThat(page0.getContent())
                .extracting(CreditTransaction::getId)
                .containsExactly(third.getId(), second.getId());

        assertThat(page1.getContent())
                .extracting(CreditTransaction::getId)
                .containsExactly(first.getId());
    }
}
