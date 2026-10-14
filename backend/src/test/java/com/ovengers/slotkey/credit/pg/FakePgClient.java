package com.ovengers.slotkey.credit.pg;

import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Profile("test")
public class FakePgClient implements PgClient {

    private final AtomicInteger confirmCalls = new AtomicInteger();
    private final AtomicInteger lookupCalls = new AtomicInteger();
    private final List<Boolean> transactionActiveDuringCalls = new CopyOnWriteArrayList<>();

    private volatile PgConfirmationResult confirmationResult =
            PgConfirmationResult.approved("payment-default");
    private volatile PgPaymentLookupResult lookupResult =
            PgPaymentLookupResult.unknown();
    private volatile Duration delay = Duration.ZERO;
    private volatile PgConfirmRequest lastConfirmRequest;

    @Override
    public PgConfirmationResult confirm(PgConfirmRequest request) {
        transactionActiveDuringCalls.add(
                TransactionSynchronizationManager.isActualTransactionActive()
        );
        lastConfirmRequest = request;
        confirmCalls.incrementAndGet();
        sleep();
        return confirmationResult;
    }

    @Override
    public PgPaymentLookupResult findByPaymentKey(String paymentKey) {
        transactionActiveDuringCalls.add(
                TransactionSynchronizationManager.isActualTransactionActive()
        );
        lookupCalls.incrementAndGet();
        sleep();
        return lookupResult;
    }

    public void returnsApproved(String paymentKey) {
        confirmationResult = PgConfirmationResult.approved(paymentKey);
    }

    public void returnsDeclined(String errorCode) {
        confirmationResult = PgConfirmationResult.declined(errorCode);
    }

    public void returnsUnknown(
            String errorCode,
            PgConfirmationResult.UnknownReason reason
    ) {
        confirmationResult = PgConfirmationResult.unknown(errorCode, reason);
    }

    public void lookupReturnsApproved(String paymentKey) {
        lookupResult = PgPaymentLookupResult.approved(paymentKey);
    }

    public void lookupReturnsFailed(String paymentKey) {
        lookupResult = PgPaymentLookupResult.failed(paymentKey);
    }

    public void lookupReturnsUnknown() {
        lookupResult = PgPaymentLookupResult.unknown();
    }

    public void delay(Duration delay) {
        this.delay = delay;
    }

    public int confirmCalls() {
        return confirmCalls.get();
    }

    public int lookupCalls() {
        return lookupCalls.get();
    }

    public PgConfirmRequest lastConfirmRequest() {
        return lastConfirmRequest;
    }

    public List<Boolean> transactionActiveDuringCalls() {
        return List.copyOf(transactionActiveDuringCalls);
    }

    public void reset() {
        confirmCalls.set(0);
        lookupCalls.set(0);
        transactionActiveDuringCalls.clear();
        confirmationResult = PgConfirmationResult.approved("payment-default");
        lookupResult = PgPaymentLookupResult.unknown();
        delay = Duration.ZERO;
        lastConfirmRequest = null;
    }

    private void sleep() {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Fake PG delay interrupted", e);
        }
    }
}
