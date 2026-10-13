package com.ovengers.slotkey.credit.repository;

import com.ovengers.slotkey.credit.entity.ChargeOrder;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.repository.MemberRepository;
import com.ovengers.slotkey.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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
                new Member("charge-order@test.com", "hash", "충전회원")
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
}
