package com.ovengers.slotkey.outbox;

import com.ovengers.slotkey.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/** 기본 설정(slotkey.kafka.enabled=false)에서는 브로커 없이 기동하고 relay·Kafka 빈이 없어야 한다. */
class OutboxRelayDisabledTest extends IntegrationTestSupport {

    @Autowired
    private ApplicationContext context;

    @Test
    void relayAndKafkaBeansAreAbsentByDefault() {
        assertThat(context.getBeanNamesForType(OutboxRelay.class)).isEmpty();
        assertThat(context.getBeanNamesForType(OutboxPublisher.class)).isEmpty();
    }
}
