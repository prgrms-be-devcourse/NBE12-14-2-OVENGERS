package com.ovengers.slotkey.credit.service;

import com.ovengers.slotkey.credit.dto.response.ChargeOrderResponse;
import com.ovengers.slotkey.credit.entity.ChargeOrder;
import com.ovengers.slotkey.credit.repository.ChargeOrderRepository;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.entity.MemberStatus;
import com.ovengers.slotkey.member.repository.MemberRepository;
import com.ovengers.slotkey.credit.dto.response.ChargeOrderHistoryResponse;
import com.ovengers.slotkey.credit.entity.ChargeOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ChargeOrderService {

    private static final long MIN_AMOUNT = 1_000L;
    private static final long MAX_AMOUNT = 100_000L;
    private static final long AMOUNT_UNIT = 100L;

    private final ChargeOrderRepository chargeOrderRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;

    public ChargeOrderService(
            ChargeOrderRepository chargeOrderRepository,
            MemberRepository memberRepository,
            Clock clock
    ) {
        this.chargeOrderRepository = chargeOrderRepository;
        this.memberRepository = memberRepository;
        this.clock = clock;
    }

    @Transactional
    public ChargeOrderResponse create(Long memberId, long amount) {
        validateAmount(amount);
        validateActiveMember(memberId);

        ChargeOrder chargeOrder = ChargeOrder.createPending(
                memberId,
                generateOrderId(),
                amount,
                LocalDateTime.now(clock)
        );
        return ChargeOrderResponse.from(chargeOrderRepository.save(chargeOrder));
    }

    @Transactional(readOnly = true)
    public ChargeOrderResponse get(Long memberId, String orderId) {
        ChargeOrder chargeOrder = chargeOrderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHARGE_ORDER_NOT_FOUND));
        if (!chargeOrder.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_NOT_OWNER);
        }
        return ChargeOrderResponse.from(chargeOrder);
    }

    /** 내 충전 내역. 결제가 시도되지 않은 PENDING 주문은 노출하지 않는다. */
    @Transactional(readOnly = true)
    public Page<ChargeOrderHistoryResponse> getMyHistory(Long memberId, Pageable pageable) {
        return chargeOrderRepository.findByMemberIdAndStatusInOrderByIdDesc(
                memberId,
                Set.of(ChargeOrderStatus.PAID, ChargeOrderStatus.CONFIRMING, ChargeOrderStatus.FAILED),
                pageable
        ).map(ChargeOrderHistoryResponse::from);
    }

    private void validateActiveMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED));
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE);
        }
    }

    private void validateAmount(long amount) {
        if (amount < MIN_AMOUNT || amount > MAX_AMOUNT || amount % AMOUNT_UNIT != 0) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "충전 금액은 1,000원 이상 100,000원 이하의 100원 단위여야 합니다."
            );
        }
    }

    private String generateOrderId() {
        return "chg_" + UUID.randomUUID().toString().toLowerCase(Locale.ROOT);
    }
}
