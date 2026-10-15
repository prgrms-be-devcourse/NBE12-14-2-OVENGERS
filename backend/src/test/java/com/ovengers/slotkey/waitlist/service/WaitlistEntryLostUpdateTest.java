package com.ovengers.slotkey.waitlist.service;

import com.ovengers.slotkey.support.ReservationIntegrationTestSupport;
import com.ovengers.slotkey.waitlist.entity.WaitlistEntry;
import com.ovengers.slotkey.waitlist.entity.WaitlistStatus;
import com.ovengers.slotkey.waitlist.repository.WaitlistEntryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 4-0: 취소(cancel)와 알림 시각 기록(markNotified)이 같은 대기 행에서 겹칠 때 서로를 덮어쓰지 않는지.
 *
 * 시나리오: 스레드 A가 엔터티를 먼저 읽는다(lastNotifiedAt = null 스냅샷) -> 스레드 B가 알림 시각을 기록하고
 * 커밋한다 -> A가 cancel()하고 커밋한다. 전체 컬럼 UPDATE면 A의 flush가 B가 쓴 last_notified_at을
 * 스냅샷 값(null)으로 되돌린다(lost update, step-1-review.md M3).
 * 래치로 순서를 고정하므로 타이밍 운이 아니라 매번 같은 결과가 나온다.
 */
class WaitlistEntryLostUpdateTest extends ReservationIntegrationTestSupport {

    @Autowired
    private WaitlistEntryRepository waitlistEntryRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("취소가 먼저 읽은 뒤 알림 시각이 기록돼도, 취소 커밋이 last_notified_at을 되돌리지 않는다")
    void cancel_afterConcurrentMarkNotified_keepsLastNotifiedAt() throws Exception {
        Long memberId = createMember(0);
        Long spaceId = createSpace();
        LocalDateTime start = tomorrowAt(14, 0);
        Long entryId = waitlistEntryRepository.saveAndFlush(
                WaitlistEntry.waiting(memberId, spaceId, start, start.plusHours(1), now())).getId();
        LocalDateTime notifiedAt = now().plusMinutes(1);

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CountDownLatch aHasRead = new CountDownLatch(1);
        CountDownLatch bCommitted = new CountDownLatch(1);

        CompletableFuture<Void> a = CompletableFuture.runAsync(() -> tx.executeWithoutResult(status -> {
            WaitlistEntry entry = waitlistEntryRepository.findById(entryId).orElseThrow();
            aHasRead.countDown();
            await(bCommitted);
            entry.cancel();
        }));
        CompletableFuture<Integer> b = CompletableFuture.supplyAsync(() -> {
            await(aHasRead);
            Integer updated = tx.execute(status ->
                    waitlistEntryRepository.markNotified(entryId, notifiedAt, WaitlistStatus.WAITING));
            bCommitted.countDown();
            return updated;
        });

        assertThat(b.get(10, TimeUnit.SECONDS)).isEqualTo(1);
        a.get(10, TimeUnit.SECONDS);

        WaitlistEntry result = waitlistEntryRepository.findById(entryId).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(WaitlistStatus.CANCELLED);
        assertThat(result.getActive()).isNull();
        assertThat(result.getLastNotifiedAt()).isEqualTo(notifiedAt);
    }

    @Test
    @DisplayName("이미 취소된 대기 행에는 알림 시각을 기록하지 않는다(0건 갱신)")
    void markNotified_onCancelledEntry_updatesNothing() {
        Long memberId = createMember(0);
        Long spaceId = createSpace();
        LocalDateTime start = tomorrowAt(14, 0);
        WaitlistEntry entry = WaitlistEntry.waiting(memberId, spaceId, start, start.plusHours(1), now());
        entry.cancel();
        Long entryId = waitlistEntryRepository.saveAndFlush(entry).getId();

        int updated = new TransactionTemplate(transactionManager).execute(status ->
                waitlistEntryRepository.markNotified(entryId, now(), WaitlistStatus.WAITING));

        assertThat(updated).isZero();
        assertThat(waitlistEntryRepository.findById(entryId).orElseThrow().getLastNotifiedAt()).isNull();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
