package com.ovengers.slotkey.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.auditing.DateTimeProvider;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JpaAuditingConfigTest {

    @Test
    @DisplayName("Clock 빈에 설정된 시각을 DateTimeProvider가 반환한다")
    void clockDateTimeProvider_returnsClockTime() {
        Instant fixedInstant = Instant.parse("2026-09-16T12:00:00Z");
        ZoneId zoneId = ZoneId.of("Asia/Seoul");
        Clock fixedClock = Clock.fixed(fixedInstant, zoneId);

        JpaAuditingConfig jpaAuditingConfig = new JpaAuditingConfig(fixedClock);
        DateTimeProvider dateTimeProvider = jpaAuditingConfig.clockDateTimeProvider();

        Optional<TemporalAccessor> now = dateTimeProvider.getNow();
        assertThat(now).isPresent();
        assertThat(now.get()).isEqualTo(LocalDateTime.now(fixedClock));
    }
}

