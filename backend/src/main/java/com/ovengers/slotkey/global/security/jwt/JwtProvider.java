package com.ovengers.slotkey.global.security.jwt;

import com.ovengers.slotkey.member.entity.Member;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.time.LocalDateTime;
import java.time.Clock;

@Component
public class JwtProvider {

    @Value("${custom.jwt.secret-key}")
    private String secretKey;

    @Value("${custom.jwt.expire-millis}")
    private long expireMillis;

    private final Clock clock;

    public JwtProvider(Clock clock) {
        this.clock = clock;
    }

    public String genAccessToken(
            Member member,
            String sessionId,
            LocalDateTime sessionExpiresAt
    ) {
        return JwtUtil.createToken(
                secretKey,
                expireMillis,
                Map.of(
                        "id", member.getId(),
                        "email", member.getEmail(),
                        "role", member.getRole().name(),
                        "sid", sessionId
                ),
                sessionExpiresAt,
                clock
        );
    }

    public Map<String, Object> payloadOrNull(String token) {
        return JwtUtil.payloadOrNull(token, secretKey, clock);
    }
}
