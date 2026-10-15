package com.ovengers.slotkey.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 전송 대기 > delivery.timeout > request.timeout 이어야 타임아웃으로 실패 처리한 레코드가
 * 프로듀서 버퍼에 남았다가 뒤늦게 전달(중복)되지 않는다. 기본값(application.yml)이 이 부등식을 지키는지 본다.
 */
class KafkaTimeoutDefaultsTest {

    @Test
    void defaultsSatisfySendWaitGreaterThanDeliveryGreaterThanRequest() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties p = yaml.getObject();

        long sendWait = Long.parseLong(p.getProperty("slotkey.kafka.send-timeout-ms"));
        long delivery = Long.parseLong(p.getProperty("slotkey.kafka.delivery-timeout-ms"));
        long request = Long.parseLong(p.getProperty("slotkey.kafka.request-timeout-ms"));

        assertThat(sendWait).isGreaterThan(delivery);
        assertThat(delivery).isGreaterThan(request);
        // 프로듀서 설정이 위 값을 그대로 쓰는지(한 곳에서만 정의)
        assertThat(p.getProperty("spring.kafka.producer.properties[delivery.timeout.ms]"))
                .isEqualTo("${slotkey.kafka.delivery-timeout-ms}");
        assertThat(p.getProperty("spring.kafka.producer.properties[request.timeout.ms]"))
                .isEqualTo("${slotkey.kafka.request-timeout-ms}");
    }
}
