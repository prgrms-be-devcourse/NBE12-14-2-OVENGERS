package com.ovengers.slotkey.credit.service;

import com.ovengers.slotkey.audit.entity.AuditAction;
import com.ovengers.slotkey.audit.entity.AuditTargetType;
import com.ovengers.slotkey.audit.service.AuditLogService;
import com.ovengers.slotkey.credit.dto.response.ChargeOrderConfirmResponse;
import com.ovengers.slotkey.credit.entity.ChargeOrder;
import com.ovengers.slotkey.credit.entity.ChargeOrderStatus;
import com.ovengers.slotkey.credit.entity.CreditTransaction;
import com.ovengers.slotkey.credit.pg.PgConfirmRequest;
import com.ovengers.slotkey.credit.repository.ChargeOrderRepository;
import com.ovengers.slotkey.credit.repository.CreditTransactionRepository;
import com.ovengers.slotkey.global.error.BusinessException;
import com.ovengers.slotkey.global.error.ErrorCode;
import com.ovengers.slotkey.global.idempotency.IdempotencyService;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.entity.MemberStatus;
import com.ovengers.slotkey.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class ChargeOrderConfirmTransactionService {

    static final String REQUEST_PATH = "/credits/charge-orders/confirm";

    private final ChargeOrderRepository chargeOrderRepository;
    private final MemberRepository memberRepository;
    private final CreditTransactionRepository creditTransactionRepository;
    private final IdempotencyService idempotencyService;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public ChargeOrderConfirmTransactionService(
            ChargeOrderRepository chargeOrderRepository,
            MemberRepository memberRepository,
            CreditTransactionRepository creditTransactionRepository,
            IdempotencyService idempotencyService,
            AuditLogService auditLogService,
            Clock clock
    ) {
        this.chargeOrderRepository = chargeOrderRepository;
        this.memberRepository = memberRepository;
        this.creditTransactionRepository = creditTransactionRepository;
        this.idempotencyService = idempotencyService;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    @Transactional
    public ChargeOrderPreparation prepare(
            Long memberId,
            String paymentKey,
            String orderId,
            long amount,
            String clientIdempotencyKey
    ) {
        ChargeOrder order = chargeOrderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHARGE_ORDER_NOT_FOUND));

        ErrorCode requesterRejection = validateRequester(order, memberId);
        if (requesterRejection != null) {
            return ChargeOrderPreparation.reject(requesterRejection);
        }

        boolean amountMismatch = order.getAmount() != amount;
        if (amountMismatch) {
            auditLogService.log(
                    memberId,
                    AuditAction.CHARGE_AMOUNT_MISMATCH,
                    AuditTargetType.CHARGE_ORDER,
                    order.getId(),
                    "충전 승인 요청 금액 불일치",
                    Map.of("storedAmount", order.getAmount()),
                    Map.of("requestedAmount", amount)
            );
        }

        ErrorCode rejection = validateRequestDetails(
                order,
                memberId,
                orderId,
                clientIdempotencyKey,
                amountMismatch
        );
        if (rejection != null) {
            return ChargeOrderPreparation.reject(rejection);
        }

        ChargeOrderPreparation existing = currentState(order, paymentKey);
        if (existing != null) {
            return existing;
        }

        String pgIdempotencyKey = UUID.randomUUID().toString();
        LocalDateTime confirmingAt = LocalDateTime.now(clock);
        int updated = chargeOrderRepository.startConfirming(
                order.getId(),
                ChargeOrderStatus.PENDING,
                ChargeOrderStatus.CONFIRMING,
                paymentKey,
                clientIdempotencyKey,
                pgIdempotencyKey,
                confirmingAt,
                confirmingAt.plusMinutes(1)
        );
        if (updated == 1) {
            return ChargeOrderPreparation.callPg(new PgConfirmRequest(
                    paymentKey,
                    orderId,
                    amount,
                    pgIdempotencyKey
            ));
        }

        ChargeOrder concurrent = chargeOrderRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHARGE_ORDER_NOT_FOUND));
        ChargeOrderPreparation concurrentState = currentState(concurrent, paymentKey);
        if (concurrentState != null) {
            return concurrentState;
        }
        throw new BusinessException(ErrorCode.CHARGE_ORDER_STATE_CONFLICT);
    }

    @Transactional
    public ChargeOrderConfirmResponse completeApproved(
            String orderId,
            String paymentKey,
            String clientIdempotencyKey
    ) {
        ChargeOrder order = chargeOrderRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHARGE_ORDER_NOT_FOUND));

        if (order.getStatus() == ChargeOrderStatus.PAID
                && paymentKey.equals(order.getPaymentKey())) {
            return ChargeOrderConfirmResponse.from(order);
        }
        if (order.getStatus() != ChargeOrderStatus.CONFIRMING
                || !paymentKey.equals(order.getPaymentKey())) {
            throw new BusinessException(ErrorCode.CHARGE_ORDER_STATE_CONFLICT);
        }

        Member member = memberRepository.findByIdForUpdate(order.getMemberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        int amount = Math.toIntExact(order.getAmount());
        if ((long) member.getBalance() + amount > Integer.MAX_VALUE) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "크레딧 잔액이 허용 범위를 초과합니다."
            );
        }

        member.addCredits(amount);
        LocalDateTime now = LocalDateTime.now(clock);
        creditTransactionRepository.save(CreditTransaction.topUp(
                member,
                amount,
                order,
                member.getBalance(),
                now
        ));
        order.markPaid(now);

        ChargeOrderConfirmResponse response = ChargeOrderConfirmResponse.from(order);
        idempotencyService.save(
                clientIdempotencyKey,
                order.getMemberId(),
                REQUEST_PATH,
                200,
                response
        );
        return response;
    }

    /**
     * PG가 알려 준 결제수단을 표시용으로 기록한다. 충전 확정(completeApproved)과 별도 트랜잭션이라
     * 이 기록이 실패해도 크레딧 충전에는 영향이 없다.
     */
    @Transactional
    public void recordPaymentMethod(String orderId, String paymentMethod, String paymentProvider) {
        chargeOrderRepository.findByOrderId(orderId)
                .ifPresent(order -> order.recordPaymentMethod(paymentMethod, paymentProvider));
    }

    @Transactional
    public void completeDeclined(String orderId, String errorCode) {
        ChargeOrder order = chargeOrderRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHARGE_ORDER_NOT_FOUND));
        if (order.getStatus() == ChargeOrderStatus.CONFIRMING) {
            order.markFailed(errorCode, LocalDateTime.now(clock));
        }
    }

    @Transactional(readOnly = true)
    public ChargeOrderConfirmResponse currentResponse(String orderId) {
        ChargeOrder order = chargeOrderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHARGE_ORDER_NOT_FOUND));
        return ChargeOrderConfirmResponse.from(order);
    }

    private ChargeOrderPreparation currentState(ChargeOrder order, String paymentKey) {
        if (order.getStatus() == ChargeOrderStatus.PENDING) {
            return null;
        }
        if (order.getStatus() == ChargeOrderStatus.PAID
                && paymentKey.equals(order.getPaymentKey())) {
            return ChargeOrderPreparation.current(ChargeOrderConfirmResponse.from(order));
        }
        if (order.getStatus() == ChargeOrderStatus.CONFIRMING) {
            return ChargeOrderPreparation.current(ChargeOrderConfirmResponse.from(order));
        }
        throw new BusinessException(ErrorCode.CHARGE_ORDER_STATE_CONFLICT);
    }

    private ErrorCode validateRequester(ChargeOrder order, Long memberId) {
        if (!order.getMemberId().equals(memberId)) {
            return ErrorCode.FORBIDDEN_NOT_OWNER;
        }

        Member member = memberRepository.findById(memberId).orElse(null);
        if (member == null) {
            return ErrorCode.AUTHENTICATION_REQUIRED;
        }
        if (member.getStatus() != MemberStatus.ACTIVE) {
            return ErrorCode.ACCOUNT_INACTIVE;
        }
        return null;
    }

    private ErrorCode validateRequestDetails(
            ChargeOrder order,
            Long memberId,
            String orderId,
            String clientIdempotencyKey,
            boolean amountMismatch
    ) {
        boolean reused = chargeOrderRepository.findByMemberIdAndClientIdempotencyKey(
                        memberId,
                        clientIdempotencyKey
                )
                .filter(claimed -> !claimed.getOrderId().equals(orderId))
                .isPresent();
        if (reused) {
            return ErrorCode.IDEMPOTENCY_KEY_REUSED;
        }
        if (amountMismatch) {
            return ErrorCode.CHARGE_ORDER_AMOUNT_MISMATCH;
        }
        return null;
    }
}
