package com.ovengers.slotkey.outbox;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@ConditionalOnProperty(name = "slotkey.kafka.enabled", havingValue = "true")
public class KafkaOutboxPublisher implements OutboxPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final long sendTimeoutMs;

    public KafkaOutboxPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                @Value("${slotkey.kafka.send-timeout-ms}") long sendTimeoutMs) {
        this.kafkaTemplate = kafkaTemplate;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    @Override
    public void publish(String topic, String key, String payload) {
        try {
            kafkaTemplate.send(topic, key, payload).get(sendTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OutboxPublishException("Interrupted while sending to topic " + topic, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new OutboxPublishException("Failed to send to topic " + topic, e);
        }
    }
}
