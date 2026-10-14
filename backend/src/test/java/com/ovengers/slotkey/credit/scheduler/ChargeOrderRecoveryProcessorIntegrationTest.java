package com.ovengers.slotkey.credit.scheduler;

import com.ovengers.slotkey.credit.dto.response.ChargeOrderResponse;
import com.ovengers.slotkey.credit.pg.FakePgClient;
import com.ovengers.slotkey.credit.pg.PgConfirmationResult;
import com.ovengers.slotkey.credit.service.ChargeOrderConfirmService;
import com.ovengers.slotkey.credit.service.ChargeOrderService;
import com.ovengers.slotkey.member.entity.Member;
import com.ovengers.slotkey.member.entity.MemberStatus;
import com.ovengers.slotkey.member.repository.MemberRepository;
import com.ovengers.slotkey.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class ChargeOrderRecoveryProcessorIntegrationTest extends IntegrationTestSupport {

    private static final long AMOUNT = 1_000L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 14, 12, 0);

    @Autowired
    private ChargeOrderService chargeOrderService;

    @Autowired
    private ChargeOrderConfirmService chargeOrderConfirmService;

    @Autowired
    private ChargeOrderRecoveryProcessor processor;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private FakePgClient fakePgClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long memberId;
    private Long chargeOrderId;
    private String orderId;
    private String paymentKey;
    private String clientIdempotencyKey;

    @BeforeEach
    void setUp() {
        fakePgClient.reset();
        Member member = memberRepository.saveAndFlush(new Member(
                "recovery-" + UUID.randomUUID() + "@test.com",
                "복구회원"
        ));
        memberId = member.getId();
        ChargeOrderResponse order = chargeOrderService.create(memberId, AMOUNT);
        orderId = order.orderId();
        paymentKey = "payment-" + UUID.randomUUID();
        clientIdempotencyKey = "client-" + UUID.randomUUID();

        fakePgClient.returnsUnknown(
                "COMMON_ERROR",
                PgConfirmationResult.UnknownReason.INDETERMINATE
        );
        chargeOrderConfirmService.confirm(
                memberId,
                paymentKey,
                orderId,
                AMOUNT,
                clientIdempotencyKey
        );
        chargeOrderId = jdbcTemplate.queryForObject(
                "SELECT id FROM charge_order WHERE order_id = ?",
                Long.class,
                orderId
        );
        jdbcTemplate.update(
                "UPDATE charge_order SET updated_at = ?, recovery_next_attempt_at = ? WHERE id = ?",
                NOW.minusMinutes(2),
                NOW.minusMinutes(1),
                chargeOrderId
        );
        fakePgClient.reset();
    }

    @AfterEach
    void cleanUpChargeData() {
        jdbcTemplate.update("DELETE FROM audit_logs WHERE target_type = 'CHARGE_ORDER'");
        jdbcTemplate.update("DELETE FROM credit_transaction");
        jdbcTemplate.update("DELETE FROM charge_order");
        jdbcTemplate.update("DELETE FROM idempotency_key");
    }

    @Test
    @DisplayName("PG 조회가 승인이고 결제 정보가 일치하면 기존 DB 2로 PAID와 TOP_UP을 반영한다")
    void recover_approved_marksPaidAndTopUpsThroughExistingDb2() {
        fakePgClient.lookupReturnsApproved(
                paymentKey,
                orderId,
                AMOUNT,
                "KRW"
        );

        processor.recover(chargeOrderId, NOW);

        assertThat(status()).isEqualTo("PAID");
        assertThat(balance()).isEqualTo(AMOUNT);
        assertThat(topUpCount()).isEqualTo(1);
        assertThat(idempotencyCount()).isEqualTo(1);
        assertThat(fakePgClient.transactionActiveDuringCalls()).containsExactly(false);
    }

    @Test
    @DisplayName("승인 뒤 회원이 비활성화돼도 이미 결제된 금액은 크레딧으로 반영한다")
    void recover_approvedAfterMemberSuspended_stillTopUps() {
        Member member = memberRepository.findById(memberId).orElseThrow();
        member.updateStatus(MemberStatus.SUSPENDED);
        memberRepository.saveAndFlush(member);
        fakePgClient.lookupReturnsApproved(paymentKey, orderId, AMOUNT, "KRW");

        processor.recover(chargeOrderId, NOW);

        assertThat(status()).isEqualTo("PAID");
        assertThat(balance()).isEqualTo(AMOUNT);
        assertThat(topUpCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("PG 조회가 실패면 원시 상태를 failure_code에 남기고 FAILED로 전이한다")
    void recover_failed_marksFailedWithRawPgStatus() {
        fakePgClient.lookupReturnsFailed(
                paymentKey,
                orderId,
                AMOUNT,
                "KRW",
                "EXPIRED"
        );

        processor.recover(chargeOrderId, NOW);

        assertThat(status()).isEqualTo("FAILED");
        assertThat(failureCode()).isEqualTo("PG_STATUS_EXPIRED");
        assertThat(topUpCount()).isZero();
        assertThat(balance()).isZero();
    }

    @Test
    @DisplayName("PG 조회 결과를 모르면 CONFIRMING을 유지하고 다음 조회를 1분 뒤로 미룬다")
    void recover_unknown_reschedulesAfterOneMinute() {
        fakePgClient.lookupReturnsUnknown(
                paymentKey,
                orderId,
                AMOUNT,
                "KRW",
                "IN_PROGRESS"
        );

        processor.recover(chargeOrderId, NOW);

        assertThat(status()).isEqualTo("CONFIRMING");
        assertThat(nextAttemptAt()).isEqualTo(NOW.plusMinutes(1));
        assertThat(topUpCount()).isZero();
    }

    @Test
    @DisplayName("Payment 객체를 받지 못한 UNKNOWN은 무결성 대조 없이 다음 조회를 예약한다")
    void recover_unknownWithoutPaymentObject_reschedulesWithoutExhaustion() {
        fakePgClient.lookupReturnsUnknown();

        processor.recover(chargeOrderId, NOW);

        assertThat(status()).isEqualTo("CONFIRMING");
        assertThat(nextAttemptAt()).isEqualTo(NOW.plusMinutes(1));
        assertThat(recoveryExhaustedAt()).isNull();
        assertThat(topUpCount()).isZero();
        assertThat(manualReviewAuditCount()).isZero();
    }

    @Test
    @DisplayName("Payment 객체를 받았지만 필수 필드가 없으면 즉시 수동 처리 대상으로 남긴다")
    void recover_unknownPaymentObjectMissingRequiredField_marksManualReview() {
        fakePgClient.lookupReturnsUnknownPaymentObject(
                paymentKey,
                orderId,
                null,
                "KRW",
                "READY"
        );

        processor.recover(chargeOrderId, NOW);
        processor.recover(chargeOrderId, NOW.plusMinutes(1));

        assertThat(status()).isEqualTo("CONFIRMING");
        assertThat(recoveryExhaustedAt()).isEqualTo(NOW);
        assertThat(nextAttemptAt()).isNull();
        assertThat(topUpCount()).isZero();
        assertManualReviewAudit("PG_PAYMENT_MISMATCH");
    }

    @Test
    @DisplayName("예상 밖 PG 조회 예외도 CONFIRMING을 유지하고 다음 조회를 예약한 뒤 전파한다")
    void recover_unexpectedLookupException_reschedulesAndPropagates() {
        fakePgClient.lookupThrows(new IllegalStateException("unexpected lookup failure"));

        assertThatThrownBy(() -> processor.recover(chargeOrderId, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("unexpected lookup failure");

        assertThat(status()).isEqualTo("CONFIRMING");
        assertThat(nextAttemptAt()).isEqualTo(NOW.plusMinutes(1));
        assertThat(topUpCount()).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("paymentMismatches")
    @DisplayName("PG 응답의 결제 식별 정보가 다르면 적립하지 않고 즉시 수동 처리 대상으로 남긴다")
    void recover_paymentMismatch_marksManualReviewWithoutTopUp(
            String name,
            String lookupPaymentKey,
            String lookupOrderId,
            Long lookupAmount,
            String lookupCurrency
    ) {
        fakePgClient.lookupReturnsApproved(
                lookupPaymentKey == null ? paymentKey : lookupPaymentKey,
                lookupOrderId == null ? orderId : lookupOrderId,
                lookupAmount == null ? AMOUNT : lookupAmount,
                lookupCurrency == null ? "KRW" : lookupCurrency
        );

        processor.recover(chargeOrderId, NOW);

        assertThat(status()).isEqualTo("CONFIRMING");
        assertThat(recoveryExhaustedAt()).isEqualTo(NOW);
        assertThat(nextAttemptAt()).isNull();
        assertThat(topUpCount()).isZero();
        assertThat(balance()).isZero();
        assertManualReviewAudit("PG_PAYMENT_MISMATCH");
    }

    @Test
    @DisplayName("정확히 24시간이 된 주문은 조회하지 않고 한 번만 수동 처리 경고를 남긴다")
    void markExpired_exactTwentyFourHours_marksAndWarnsOnce(CapturedOutput output) {
        jdbcTemplate.update(
                "UPDATE charge_order SET updated_at = ?, recovery_next_attempt_at = ? WHERE id = ?",
                NOW.minusHours(24),
                NOW.minusHours(23),
                chargeOrderId
        );

        processor.markExpired(chargeOrderId, NOW);
        processor.markExpired(chargeOrderId, NOW.plusMinutes(1));

        assertThat(status()).isEqualTo("CONFIRMING");
        assertThat(recoveryExhaustedAt()).isEqualTo(NOW);
        assertThat(fakePgClient.lookupCalls()).isZero();
        assertThat(output.getAll())
                .containsOnlyOnce("reason=RECOVERY_WINDOW_EXCEEDED");
        assertManualReviewAudit("RECOVERY_WINDOW_EXCEEDED");
    }

    @Test
    @DisplayName("복구 필수 시각이 없으면 PG를 호출하지 않고 즉시 수동 처리 대상으로 남긴다")
    void recover_missingNextAttempt_marksManualReviewWithoutLookup() {
        jdbcTemplate.update(
                "UPDATE charge_order SET recovery_next_attempt_at = NULL WHERE id = ?",
                chargeOrderId
        );

        processor.recover(chargeOrderId, NOW);
        processor.recover(chargeOrderId, NOW.plusMinutes(1));

        assertThat(status()).isEqualTo("CONFIRMING");
        assertThat(recoveryExhaustedAt()).isEqualTo(NOW);
        assertThat(fakePgClient.lookupCalls()).isZero();
        assertManualReviewAudit("RECOVERY_INVARIANT_VIOLATION");
    }

    @Test
    @DisplayName("이미 PAID가 된 후보를 다시 처리해도 PG 조회와 중복 적립을 하지 않는다")
    void recover_alreadyPaid_skipsLookupAndTopUp() {
        fakePgClient.lookupReturnsApproved(paymentKey, orderId, AMOUNT, "KRW");
        processor.recover(chargeOrderId, NOW);
        fakePgClient.reset();

        processor.recover(chargeOrderId, NOW.plusMinutes(1));

        assertThat(fakePgClient.lookupCalls()).isZero();
        assertThat(status()).isEqualTo("PAID");
        assertThat(topUpCount()).isEqualTo(1);
        assertThat(balance()).isEqualTo(AMOUNT);
    }

    @Test
    @DisplayName("PG 승인 조회와 로컬 FAILED 전이가 경합하면 재개방하지 않고 실제 상태를 감사 기록한다")
    void recover_approvedRacesWithLocalFailed_auditsFactsWithoutReopening(CapturedOutput output) {
        fakePgClient.lookupReturnsApproved(paymentKey, orderId, AMOUNT, "KRW");
        fakePgClient.afterLookupDelay(() -> jdbcTemplate.update(
                "UPDATE charge_order SET status = 'FAILED', failure_code = 'RACE' WHERE id = ?",
                chargeOrderId
        ));

        processor.recover(chargeOrderId, NOW);
        processor.recover(chargeOrderId, NOW.plusMinutes(1));

        assertThat(status()).isEqualTo("FAILED");
        assertThat(topUpCount()).isZero();
        assertThat(balance()).isZero();
        assertThat(output.getAll())
                .contains("reason=PG_APPROVED_LOCAL_FAILED_CONFLICT");
        assertManualReviewAudit("PG_STATUS=DONE, LOCAL_STATUS=FAILED");
    }

    @Test
    @DisplayName("같은 주문을 동시에 복구해도 TOP_UP과 잔액 증가는 한 번만 반영한다")
    void recover_sameOrderConcurrently_topUpsOnce() throws Exception {
        fakePgClient.lookupReturnsApproved(paymentKey, orderId, AMOUNT, "KRW");
        fakePgClient.delay(Duration.ofMillis(300));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> recoverConcurrently(ready, start));
            Future<?> second = executor.submit(() -> recoverConcurrently(ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertThat(fakePgClient.lookupCalls()).isEqualTo(2);
        assertThat(status()).isEqualTo("PAID");
        assertThat(topUpCount()).isEqualTo(1);
        assertThat(balance()).isEqualTo(AMOUNT);
    }

    private String status() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM charge_order WHERE id = ?",
                String.class,
                chargeOrderId
        );
    }

    private String failureCode() {
        return jdbcTemplate.queryForObject(
                "SELECT failure_code FROM charge_order WHERE id = ?",
                String.class,
                chargeOrderId
        );
    }

    private LocalDateTime nextAttemptAt() {
        return jdbcTemplate.queryForObject(
                "SELECT recovery_next_attempt_at FROM charge_order WHERE id = ?",
                LocalDateTime.class,
                chargeOrderId
        );
    }

    private LocalDateTime recoveryExhaustedAt() {
        return jdbcTemplate.queryForObject(
                "SELECT recovery_exhausted_at FROM charge_order WHERE id = ?",
                LocalDateTime.class,
                chargeOrderId
        );
    }

    private long balance() {
        return jdbcTemplate.queryForObject(
                "SELECT balance FROM member WHERE id = ?",
                Long.class,
                memberId
        );
    }

    private int topUpCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM credit_transaction WHERE charge_order_id = ?",
                Integer.class,
                chargeOrderId
        );
    }

    private int idempotencyCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM idempotency_key WHERE idempotency_key = ?",
                Integer.class,
                clientIdempotencyKey
        );
    }

    private void assertManualReviewAudit(String expectedReason) {
        assertThat(jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM audit_logs
                        WHERE action = 'CHARGE_RECOVERY_MANUAL_REVIEW'
                          AND target_type = 'CHARGE_ORDER'
                          AND target_id = ?
                          AND actor_member_id IS NULL
                          AND reason = ?
                        """,
                Integer.class,
                chargeOrderId,
                expectedReason
        )).isEqualTo(1);
    }

    private int manualReviewAuditCount() {
        return jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM audit_logs
                        WHERE action = 'CHARGE_RECOVERY_MANUAL_REVIEW'
                          AND target_type = 'CHARGE_ORDER'
                          AND target_id = ?
                        """,
                Integer.class,
                chargeOrderId
        );
    }

    private void recoverConcurrently(CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시 시작 latch 시간 초과");
            }
            processor.recover(chargeOrderId, NOW);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("복구 동시성 테스트 중단", exception);
        }
    }

    private static Stream<Arguments> paymentMismatches() {
        return Stream.of(
                Arguments.of("paymentKey 불일치", "different-payment", null, null, null),
                Arguments.of("orderId 불일치", null, "chg_different", null, null),
                Arguments.of("금액 불일치", null, null, 9_999L, null),
                Arguments.of("통화 불일치", null, null, null, "USD")
        );
    }
}
