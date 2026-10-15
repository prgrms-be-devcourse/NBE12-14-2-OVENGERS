package com.ovengers.slotkey.notification.consumer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * 컨슈머 실패 처리. Spring Kafka 3.2.4의 기본 DefaultErrorHandler는 간격 없이 총 10회 시도한 뒤 로그만 남기고
 * 건너뛴다(FixedBackOff(0, 9)). 순간적인 DB 장애에는 즉시 재시도가 의미 없으므로 1초 간격 2회 재시도(총 3회)로
 * 짧게 제한하고, 소진되면 건너뛴다. DLT(dead-letter topic)는 3차 범위다 — 건너뛴 이벤트는 로그로만 남는다.
 * (docs/decisions/outbox-kafka.md "컨슈머 결정")
 *
 * Spring Boot의 리스너 컨테이너 팩토리가 CommonErrorHandler 빈을 자동으로 사용한다.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "slotkey.kafka.enabled", havingValue = "true")
public class KafkaConsumerConfig {

    static final long RETRY_INTERVAL_MS = 1_000L;
    static final long MAX_RETRIES = 2L;

    @Bean
    DefaultErrorHandler kafkaConsumerErrorHandler() {
        DefaultErrorHandler handler = new DefaultErrorHandler(new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES));
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }
}
