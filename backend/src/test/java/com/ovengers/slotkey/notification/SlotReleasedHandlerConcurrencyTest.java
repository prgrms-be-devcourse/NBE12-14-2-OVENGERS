package com.ovengers.slotkey.notification;

import com.ovengers.slotkey.notification.consumer.SlotReleasedHandler;
import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import com.ovengers.slotkey.support.ReservationIntegrationTestSupport;
import com.ovengers.slotkey.waitlist.entity.WaitlistEntry;
import com.ovengers.slotkey.waitlist.repository.WaitlistEntryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 이벤트가 동시에 여러 번 전달돼도(at-least-once + 컨슈머 재시작·리밸런싱) 알림이 한 번만 생기는지
 * 실제 MySQL에서 검증한다. 판정자는 processed_event의 UNIQUE(consumer, event_id)이며,
 * 두 번째 INSERT IGNORE는 첫 트랜잭션이 끝날 때까지 기다린 뒤 0을 받는다.
 */
class SlotReleasedHandlerConcurrencyTest extends ReservationIntegrationTestSupport {

    private static final int THREADS = 4;

    @Autowired
    private SlotReleasedHandler handler;
    @Autowired
    private WaitlistEntryRepository waitlistEntryRepository;

    private final String eventId = UUID.randomUUID().toString();

    @AfterEach
    void cleanProcessedEvents() {
        jdbcTemplate.update("DELETE FROM processed_event WHERE event_id = ?", eventId);
    }

    @Test
    @DisplayName("같은 eventId를 4개 스레드가 동시에 처리해도 예외 없이 알림은 대기자당 1건, 처리 기록은 1건이다")
    void handle_sameEventConcurrently_notifiesOnce() {
        Long spaceId = createSpace();
        Long occupierId = createMember(0);
        LocalDateTime start = tomorrowAt(14, 0);
        LocalDateTime end = tomorrowAt(15, 0);
        Long reservationId = createReservation(occupierId, spaceId, ReservationStatus.CONFIRMED, start, end);
        jdbcTemplate.update("UPDATE reservation SET status = 'CANCELLED', cancelled_at = NOW() WHERE id = ?",
                reservationId);
        jdbcTemplate.update("DELETE FROM reservation_slot WHERE reservation_id = ?", reservationId);
        Long entry1 = waitlistEntryRepository.saveAndFlush(
                WaitlistEntry.waiting(createMember(0), spaceId, start, end, now())).getId();
        Long entry2 = waitlistEntryRepository.saveAndFlush(
                WaitlistEntry.waiting(createMember(0), spaceId, start, end, now())).getId();

        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> handler.handle(eventId, reservationId));
        }
        List<Throwable> results = runConcurrently(tasks);

        assertThat(failures(results)).isEmpty();
        assertThat(countFor(entry1)).isEqualTo(1);
        assertThat(countFor(entry2)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_event WHERE event_id = ?", Integer.class, eventId)).isEqualTo(1);
    }

    private int countFor(Long entryId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE waitlist_entry_id = ?", Integer.class, entryId);
    }
}
