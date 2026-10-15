package com.ovengers.slotkey.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * PENDING outbox 행을 id 순으로 읽어 한 건씩 발행한다. 전달 보장은 최소 1회, 인스턴스 1개를 가정한다.
 * 후보 조회는 잠금 없는 SELECT이고 상태 변경은 PK 조건부 UPDATE(건별 트랜잭션)다. 이 클래스는 트랜잭션을 열지 않으므로
 * Kafka 전송 중에는 DB 잠금을 쥐지 않는다. (status, id) 범위에 FOR UPDATE를 쓰면 취소 트랜잭션과 락 대기가 생긴다.
 */
@Component
@ConditionalOnProperty(name = "slotkey.kafka.enabled", havingValue = "true")
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository repository;
    private final OutboxPublisher publisher;
    private final Clock clock;
    private final int batchSize;

    public OutboxRelay(OutboxEventRepository repository, OutboxPublisher publisher, Clock clock,
                       @Value("${slotkey.outbox.relay.batch-size:50}") int batchSize) {
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${slotkey.outbox.relay.fixed-delay-ms:1000}",
            initialDelayString = "${slotkey.outbox.relay.initial-delay-ms:0}")
    public void relayPending() {
        for (OutboxEvent event : repository.findByStatusOrderByIdAsc("PENDING", PageRequest.ofSize(batchSize))) {
            relayOne(event);
        }
    }

    private void relayOne(OutboxEvent event) {
        try {
            publisher.publish(event.getTopic(), event.getPartitionKey(), event.getPayload());
        } catch (RuntimeException e) {
            log.warn("outbox relay failed: id={}, eventId={}", event.getId(), event.getEventId(), e);
            increaseAttempts(event);
            return;
        }
        try {
            repository.markPublished(event.getId(), LocalDateTime.now(clock));
        } catch (RuntimeException e) {
            // 전송은 성공했지만 DB 반영에 실패했다. 다음 라운드에 다시 발행되므로 중복 전달이 생긴다(at-least-once).
            log.warn("outbox relay sent but mark failed: id={}, eventId={}", event.getId(), event.getEventId(), e);
            increaseAttempts(event);
        }
    }

    private void increaseAttempts(OutboxEvent event) {
        try {
            repository.increaseAttempts(event.getId());
        } catch (RuntimeException e) {
            log.warn("outbox attempts update failed: id={}", event.getId(), e);
        }
    }
}
