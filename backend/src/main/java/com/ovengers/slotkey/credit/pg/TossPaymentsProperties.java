package com.ovengers.slotkey.credit.pg;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "app.pg.toss")
public record TossPaymentsProperties(
        URI baseUrl,
        String secretKey,
        Duration connectTimeout,
        Duration readTimeout
) {
    @Override
    public String toString() {
        return "TossPaymentsProperties[baseUrl=" + baseUrl
                + ", secretKey=***"
                + ", connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout
                + "]";
    }
}
