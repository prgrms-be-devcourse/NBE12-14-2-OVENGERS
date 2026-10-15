package com.ovengers.slotkey.notification.consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.DefaultErrorHandler;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/** 에러 핸들러 설정이 설계 메모(1초 간격 2회 재시도, 해석 불가 payload는 재시도 없음)대로인지 고정한다. */
class KafkaConsumerConfigTest {

    private DefaultErrorHandler handler() throws Exception {
        Method factory = KafkaConsumerConfig.class.getDeclaredMethod("kafkaConsumerErrorHandler");
        factory.setAccessible(true);
        return (DefaultErrorHandler) factory.invoke(new KafkaConsumerConfig());
    }

    @Test
    @DisplayName("재시도는 1초 간격 2회로 정해져 있다(스프링 기본은 간격 없이 9회)")
    void retryIsShort() {
        assertThat(KafkaConsumerConfig.RETRY_INTERVAL_MS).isEqualTo(1_000L);
        assertThat(KafkaConsumerConfig.MAX_RETRIES).isEqualTo(2L);
    }

    @Test
    @DisplayName("IllegalArgumentException(해석 불가 payload)은 재시도 대상에서 빠져 있다")
    void malformedPayloadIsNotRetryable() throws Exception {
        // removeClassification은 직전 분류를 돌려준다: false = 재시도하지 않는 예외로 등록돼 있었다.
        assertThat(handler().removeClassification(IllegalArgumentException.class)).isFalse();
    }
}
