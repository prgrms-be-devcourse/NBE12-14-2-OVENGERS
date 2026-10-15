package com.ovengers.slotkey.outbox;

/** outbox 행을 외부(Kafka)로 내보내는 포트. 전송이 확인되지 않으면 {@link OutboxPublishException}을 던진다. */
public interface OutboxPublisher {
    void publish(String topic, String key, String payload);
}
