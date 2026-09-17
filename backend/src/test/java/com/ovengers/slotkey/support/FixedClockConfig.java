package com.ovengers.slotkey.support;

import com.ovengers.slotkey.global.config.ClockConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;

@TestConfiguration(proxyBeanMethods = false)
public class FixedClockConfig {

    public static final LocalDateTime FIXED_DATE_TIME = LocalDateTime.of(2026, 9, 17, 12, 0, 0);
    public static final Instant FIXED_INSTANT = FIXED_DATE_TIME.atZone(ClockConfig.DEFAULT_ZONE).toInstant();

    @Bean
    @Primary
    public Clock fixedClock() {
        return Clock.fixed(FIXED_INSTANT, ClockConfig.DEFAULT_ZONE);
    }
}
