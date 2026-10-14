package com.ovengers.slotkey.credit.repository;

import com.ovengers.slotkey.credit.entity.ChargeOrder;
import com.ovengers.slotkey.credit.entity.ChargeOrderStatus;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.repository.MemberRepository;
import com.ovengers.slotkey.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class ChargeOrderRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private ChargeOrderRepository chargeOrderRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("orderId는 PK와 별도 컬럼이며 DB UNIQUE 제약으로 중복을 거절한다")
    void save_duplicateOrderId_violatesUniqueConstraint() {
        Member member = memberRepository.save(
                new Member("charge-order@test.com", "충전회원")
        );
        String orderId = "chg_550e8400-e29b-41d4-a716-446655440000";
        LocalDateTime now = LocalDateTime.of(2026, 10, 13, 9, 0);

        ChargeOrder first = chargeOrderRepository.saveAndFlush(
                ChargeOrder.createPending(member.getId(), orderId, 1_000L, now)
        );

        assertThat(first.getId()).isNotNull();
        assertThat(first.getOrderId()).isEqualTo(orderId);
        assertThat(first.getId().toString()).isNotEqualTo(orderId);

        assertThatThrownBy(() -> chargeOrderRepository.saveAndFlush(
                ChargeOrder.createPending(member.getId(), orderId, 1_100L, now)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("CONFIRMING 전이는 최초 복구 시각을 전이 1분 뒤로 저장한다")
    void startConfirming_schedulesFirstRecoveryAfterOneMinute() {
        Member member = memberRepository.save(
                new Member("recovery-schedule@test.com", "복구회원")
        );
        LocalDateTime confirmingAt = LocalDateTime.of(2026, 10, 14, 12, 0);
        ChargeOrder order = chargeOrderRepository.saveAndFlush(
                ChargeOrder.createPending(
                        member.getId(),
                        "chg_650e8400-e29b-41d4-a716-446655440000",
                        1_000L,
                        confirmingAt.minusMinutes(5)
                )
        );

        int updated = chargeOrderRepository.startConfirming(
                order.getId(),
                ChargeOrderStatus.PENDING,
                ChargeOrderStatus.CONFIRMING,
                "payment-key",
                "client-key",
                "pg-key",
                confirmingAt,
                confirmingAt.plusMinutes(1)
        );

        ChargeOrder confirming = chargeOrderRepository.findByOrderId(order.getOrderId())
                .orElseThrow();
        assertThat(updated).isEqualTo(1);
        assertThat(confirming.getStatus()).isEqualTo(ChargeOrderStatus.CONFIRMING);
        assertThat(confirming.getUpdatedAt()).isEqualTo(confirmingAt);
        assertThat(confirming.getRecoveryNextAttemptAt())
                .isEqualTo(confirmingAt.plusMinutes(1));
    }

    @Test
    @DisplayName("복구 후보는 1분 경계를 포함하고 24시간 경계를 제외해 다음 시도 시각 순으로 조회한다")
    void findRecoveryCandidateIds_appliesTimeWindowAndStableOrder() {
        Member member = memberRepository.save(
                new Member("recovery-candidates@test.com", "후보회원")
        );
        LocalDateTime now = LocalDateTime.of(2026, 10, 14, 12, 0);
        ChargeOrder missingRecoverySchedule = confirmingOrder(member, 0, now.minusMinutes(2));
        chargeOrderRepository.findById(missingRecoverySchedule.getId())
                .orElseThrow()
                .scheduleNextRecovery(null);
        chargeOrderRepository.flush();
        ChargeOrder olderEligible = confirmingOrder(member, 1, now.minusMinutes(2));
        ChargeOrder exactOneMinute = confirmingOrder(member, 2, now.minusMinutes(1));
        confirmingOrder(member, 3, now.minusSeconds(59));
        ChargeOrder exactTwentyFourHours = confirmingOrder(member, 4, now.minusHours(24));

        List<Long> candidates = chargeOrderRepository.findRecoveryCandidateIds(
                ChargeOrderStatus.CONFIRMING,
                now,
                now.minusHours(24),
                PageRequest.of(0, 50)
        );
        List<Long> exhausted = chargeOrderRepository.findRecoveryExhaustedCandidateIds(
                ChargeOrderStatus.CONFIRMING,
                now.minusHours(24),
                PageRequest.of(0, 50)
        );

        assertThat(candidates).containsExactly(
                missingRecoverySchedule.getId(),
                olderEligible.getId(),
                exactOneMinute.getId()
        );
        assertThat(exhausted).containsExactly(exactTwentyFourHours.getId());
    }

    @Test
    @DisplayName("첫 50건의 다음 시각을 미루면 51번째 CONFIRMING 주문이 다음 조회에 포함된다")
    void findRecoveryCandidateIds_rescheduledFirstBatchDoesNotStarveNextOrder() {
        Member member = memberRepository.save(
                new Member("recovery-fairness@test.com", "공정성회원")
        );
        LocalDateTime now = LocalDateTime.of(2026, 10, 14, 12, 0);
        for (int sequence = 1; sequence <= 51; sequence++) {
            confirmingOrder(member, 100 + sequence, now.minusMinutes(2));
        }
        PageRequest firstFifty = PageRequest.of(0, 50);

        List<Long> firstBatch = chargeOrderRepository.findRecoveryCandidateIds(
                ChargeOrderStatus.CONFIRMING,
                now,
                now.minusHours(24),
                firstFifty
        );
        firstBatch.forEach(id -> chargeOrderRepository.findById(id)
                .orElseThrow()
                .scheduleNextRecovery(now.plusMinutes(1)));

        List<Long> nextBatch = chargeOrderRepository.findRecoveryCandidateIds(
                ChargeOrderStatus.CONFIRMING,
                now,
                now.minusHours(24),
                firstFifty
        );

        assertThat(firstBatch).hasSize(50);
        assertThat(nextBatch).hasSize(1);
        assertThat(firstBatch).doesNotContain(nextBatch.get(0));
    }

    private ChargeOrder confirmingOrder(Member member, int sequence, LocalDateTime confirmingAt) {
        ChargeOrder order = chargeOrderRepository.saveAndFlush(
                ChargeOrder.createPending(
                        member.getId(),
                        "chg_750e8400-e29b-41d4-a716-44665544000" + sequence,
                        1_000L,
                        confirmingAt.minusMinutes(5)
                )
        );
        chargeOrderRepository.startConfirming(
                order.getId(),
                ChargeOrderStatus.PENDING,
                ChargeOrderStatus.CONFIRMING,
                "payment-key-" + sequence,
                "client-key-" + sequence,
                "pg-key-" + sequence,
                confirmingAt,
                confirmingAt.plusMinutes(1)
        );
        return order;
    }
}
