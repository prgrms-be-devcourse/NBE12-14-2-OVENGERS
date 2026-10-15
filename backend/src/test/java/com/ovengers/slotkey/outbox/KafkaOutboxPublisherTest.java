package com.ovengers.slotkey.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaOutboxPublisherTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
    private final KafkaOutboxPublisher publisher = new KafkaOutboxPublisher(template, 100);

    @Test
    void sendsWithRowTopicAndPartitionKey() {
        when(template.send("slot-released", "7", "{}")).thenReturn(CompletableFuture.completedFuture(null));

        publisher.publish("slot-released", "7", "{}");

        verify(template).send("slot-released", "7", "{}");
    }

    @Test
    void brokerFailureIsRaisedAsException() {
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("broker down"));
        when(template.send("slot-released", "7", "{}")).thenReturn(failed);

        assertThatThrownBy(() -> publisher.publish("slot-released", "7", "{}"))
                .isInstanceOf(OutboxPublishException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void interruptRestoresFlagAndRaisesException() throws Exception {
        CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);
        when(future.get(100L, TimeUnit.MILLISECONDS)).thenThrow(new InterruptedException());
        when(template.send("slot-released", "7", "{}")).thenReturn(future);

        try {
            assertThatThrownBy(() -> publisher.publish("slot-released", "7", "{}"))
                    .isInstanceOf(OutboxPublishException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted(); // 다른 테스트에 플래그가 새지 않게 비운다
        }
    }

    @Test
    void sendWaitsOnlyUpToTimeout() {
        when(template.send("slot-released", "7", "{}")).thenReturn(new CompletableFuture<>());

        long start = System.nanoTime();
        assertThatThrownBy(() -> publisher.publish("slot-released", "7", "{}"))
                .isInstanceOf(OutboxPublishException.class);

        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(2_000);
    }
}
