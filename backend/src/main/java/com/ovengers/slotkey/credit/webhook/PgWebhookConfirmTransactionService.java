package com.ovengers.slotkey.credit.webhook;

import com.ovengers.slotkey.credit.entity.ChargeOrder;
import com.ovengers.slotkey.credit.entity.ChargeOrderStatus;
import com.ovengers.slotkey.credit.repository.ChargeOrderRepository;
import com.ovengers.slotkey.credit.service.ChargeOrderConfirmTransactionService;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 웹훅 처리에서 기존 DB 2를 호출하되, 이 호출이 실제로 PAID로 전이시켰는지 구분한다(pg-charge.md 5단계 결정 4-2).
 *
 * 주문 행을 먼저 비관적으로 잠그고 이미 같은 paymentKey로 PAID면 DB 2를 부르지 않는다. 아니면 같은 트랜잭션 안에서
 * 기존 {@link ChargeOrderConfirmTransactionService#completeApproved}를 호출한다(REQUIRED 전파로 합류, 같은 행 재잠금은
 * 같은 트랜잭션이라 대기 없음). 잠금 순서는 DB 2와 같은 주문 → 회원이다. 확정 로직은 여기 없다.
 */
@Service
public class PgWebhookConfirmTransactionService {

    private final ChargeOrderRepository chargeOrderRepository;
    private final ChargeOrderConfirmTransactionService confirmTransactionService;

    public PgWebhookConfirmTransactionService(
            ChargeOrderRepository chargeOrderRepository,
            ChargeOrderConfirmTransactionService confirmTransactionService
    ) {
        this.chargeOrderRepository = chargeOrderRepository;
        this.confirmTransactionService = confirmTransactionService;
    }

    /** 이 호출이 PAID로 전이시켰으면 true, 이미 같은 paymentKey로 PAID였으면 false. 그 밖은 DB 2의 예외를 그대로 던진다. */
    @Transactional
    public boolean completeIfNotPaid(String orderId, String paymentKey, String clientIdempotencyKey) {
        ChargeOrder order = chargeOrderRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHARGE_ORDER_NOT_FOUND));
        if (order.getStatus() == ChargeOrderStatus.PAID
                && paymentKey.equals(order.getPaymentKey())) {
            return false;
        }
        confirmTransactionService.completeApproved(orderId, paymentKey, clientIdempotencyKey);
        return true;
    }
}
