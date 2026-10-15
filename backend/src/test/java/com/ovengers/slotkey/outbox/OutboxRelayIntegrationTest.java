package com.ovengers.slotkey.outbox;

import com.ovengers.slotkey.reservation.entity.ReservationStatus;
import com.ovengers.slotkey.reservation.service.ReservationCancelService;
import com.ovengers.slotkey.support.ReservationIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "slotkey.kafka.enabled=true",
        "slotkey.outbox.relay.batch-size=2",
        "slotkey.outbox.relay.fixed-delay-ms=3600000",
        "slotkey.outbox.relay.initial-delay-ms=3600000"
})
@Import(OutboxRelayIntegrationTest.FakeConfig.class)
class OutboxRelayIntegrationTest extends ReservationIntegrationTestSupport {

    @TestConfiguration
    static class FakeConfig {
        @Bean
        @Primary
        FakeOutboxPublisher fakeOutboxPublisher() {
            return new FakeOutboxPublisher();
        }
    }

    @Autowired private OutboxRelay relay;
    @Autowired private FakeOutboxPublisher fake;
    @Autowired private OutboxEventRepository outboxRepository;
    @Autowired private ReservationCancelService cancelService;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private ApplicationContext context;

    private final String key = "relay-" + System.nanoTime();

    @BeforeEach
    void clean() {
        // 앞선 테스트가 남긴 PENDING이 이 테스트의 relay 배치를 차지하지 않게 한다.
        jdbcTemplate.update("DELETE FROM outbox_event");
        fake.reset();
    }

    @AfterEach
    void removeSeeds() {
        jdbcTemplate.update("DELETE FROM outbox_event WHERE partition_key = ?", key);
    }

    @Test
    void enabledContextStartsWithoutBrokerAndHasRelayAndKafkaPublisher() {
        assertThat(context.getBeanNamesForType(OutboxRelay.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(KafkaOutboxPublisher.class)).hasSize(1);
    }

    @Test
    void successMarksPublishedWithTimestampUsingRowTopicAndKey() {
        long id = seed("a");

        relay.relayPending();

        assertThat(fake.sent()).extracting(FakeOutboxPublisher.Sent::topic, FakeOutboxPublisher.Sent::key)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("slot-released", key));
        assertThat(statusOfEvent(id)).isEqualTo("PUBLISHED");
        assertThat(publishedAt(id)).isNotNull();
        assertThat(attempts(id)).isZero();
    }

    @Test
    void failureRaisesAttemptsAndContinuesWithNextRow() {
        long first = seed("fail-me");
        long second = seed("ok");
        fake.failWhen(payload -> payload.contains("fail-me"));

        relay.relayPending();

        assertThat(statusOfEvent(first)).isEqualTo("PENDING");
        assertThat(attempts(first)).isEqualTo(1);
        assertThat(publishedAt(first)).isNull();
        assertThat(statusOfEvent(second)).isEqualTo("PUBLISHED");

        fake.failWhen(payload -> false);
        relay.relayPending();

        assertThat(statusOfEvent(first)).isEqualTo("PUBLISHED");
        assertThat(attempts(first)).isEqualTo(1);
    }

    @Test
    void processesAtMostBatchSizeRowsInIdOrder() {
        long a = seed("a");
        long b = seed("b");
        long c = seed("c");

        relay.relayPending();

        assertThat(statusOfEvent(a)).isEqualTo("PUBLISHED");
        assertThat(statusOfEvent(b)).isEqualTo("PUBLISHED");
        assertThat(statusOfEvent(c)).isEqualTo("PENDING");
    }

    @Test
    void publishedRowsAreNotPickedUpAgain() {
        seed("a");

        relay.relayPending();
        relay.relayPending();

        assertThat(fake.sent()).hasSize(1);
    }

    @Test
    void conditionalUpdateAppliesOnlyOnce() {
        long id = seed("a");
        LocalDateTime t1 = now();

        assertThat(outboxRepository.markPublished(id, t1)).isEqualTo(1);
        assertThat(outboxRepository.markPublished(id, t1.plusHours(1))).isZero();
        assertThat(outboxRepository.increaseAttempts(id)).isZero();

        assertThat(publishedAt(id)).isEqualTo(t1);
        assertThat(attempts(id)).isZero();
    }

    /**
     * relay가 도는 동안 취소 트랜잭션이 열려 있어도(outbox INSERT를 이미 한 상태) 서로 기다리지 않아야 한다.
     * 취소 트랜잭션을 커밋 직전에 붙잡아 두고 그 사이 relay 한 번을 끝까지 돌려 실제로 겹치게 한다.
     * relay가 (status, id) 범위를 FOR UPDATE로 잠그면 여기서 relay가 타임아웃으로 실패한다.
     */
    @Test
    void relayDoesNotBlockAnOpenCancelTransaction() throws Exception {
        long seeded = seed("seeded");
        Long member = createMember(0);
        Long space = createSpace();
        Long reservation = createReservation(member, space, ReservationStatus.CONFIRMED,
                tomorrowAt(14, 0), tomorrowAt(15, 0));

        CountDownLatch cancelInserted = new CountDownLatch(1);
        CountDownLatch relayFinished = new CountDownLatch(1);
        CompletableFuture<Void> cancel = CompletableFuture.runAsync(() ->
                transactionTemplate.executeWithoutResult(tx -> {
                    cancelService.cancel(member, reservation);
                    cancelInserted.countDown();
                    await(relayFinished);
                }));
        assertThat(cancelInserted.await(10, TimeUnit.SECONDS)).isTrue();

        try {
            CompletableFuture.runAsync(relay::relayPending).get(5, TimeUnit.SECONDS);
            assertThat(statusOfEvent(seeded)).isEqualTo("PUBLISHED");
        } finally {
            relayFinished.countDown();
        }
        cancel.get(10, TimeUnit.SECONDS);

        assertThat(statusOf(reservation)).isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM outbox_event WHERE partition_key = ?", String.class, space.toString()))
                .isEqualTo("PENDING");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private long seed(String tag) {
        String eventId = UUID.randomUUID().toString();
        jdbcTemplate.update("INSERT INTO outbox_event (event_id, event_type, topic, partition_key, payload, status, attempts, created_at)"
                        + " VALUES (?, 'SlotReleased', 'slot-released', ?, ?, 'PENDING', 0, ?)",
                eventId, key, "{\"tag\":\"" + tag + "\"}", now());
        return jdbcTemplate.queryForObject("SELECT id FROM outbox_event WHERE event_id = ?", Long.class, eventId);
    }

    private String statusOfEvent(long id) {
        return jdbcTemplate.queryForObject("SELECT status FROM outbox_event WHERE id = ?", String.class, id);
    }

    private int attempts(long id) {
        return jdbcTemplate.queryForObject("SELECT attempts FROM outbox_event WHERE id = ?", Integer.class, id);
    }

    private LocalDateTime publishedAt(long id) {
        return jdbcTemplate.queryForObject("SELECT published_at FROM outbox_event WHERE id = ?", LocalDateTime.class, id);
    }
}
