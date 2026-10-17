package com.ovengers.slotkey.credit.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RECEIVED 웹훅 행을 주기적으로 처리한다(docs/decisions/pg-charge.md 5단계 결정 4-1).
 * 한 건의 예외가 다음 건을 막지 않는다. 테스트 프로필에서는 꺼 두고 메서드를 직접 호출한다.
 */
@Component
@ConditionalOnProperty(
        prefix = "slotkey.scheduler.pg-webhook",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class PgWebhookEventScheduler {

    private static final Logger log = LoggerFactory.getLogger(PgWebhookEventScheduler.class);

    private final PgWebhookEventStore eventStore;
    private final PgWebhookEventProcessor processor;
    private final int batchSize;

    public PgWebhookEventScheduler(
            PgWebhookEventStore eventStore,
            PgWebhookEventProcessor processor,
            @Value("${slotkey.scheduler.pg-webhook.batch-size:50}") int batchSize
    ) {
        this.eventStore = eventStore;
        this.processor = processor;
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString = "${slotkey.scheduler.pg-webhook.fixed-delay-ms:10000}",
            initialDelayString = "${slotkey.scheduler.pg-webhook.initial-delay-ms:10000}"
    )
    public void processReceivedEvents() {
        List<Long> eventIds = eventStore.findReceivedIds(batchSize);
        if (eventIds.isEmpty()) {
            return;
        }
        int completed = 0;
        for (Long eventId : eventIds) {
            try {
                processor.process(eventId);
                completed++;
            } catch (RuntimeException exception) {
                log.error(
                        "[PG_WEBHOOK] eventId={} 처리 실패 exceptionClass={}",
                        eventId,
                        exception.getClass().getSimpleName(),
                        exception
                );
            }
        }
        log.info("[PG_WEBHOOK] 대상 {}건 중 {}건 처리", eventIds.size(), completed);
    }
}
