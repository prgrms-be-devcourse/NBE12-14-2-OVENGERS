package com.ovengers.slotkey.global.security.jwt;

import com.ovengers.slotkey.member.entity.Member;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class JwtProvider {

    @Value("${custom.jwt.secret-key}")
    private String secretKey;

    @Value("${custom.jwt.expire-millis}")
    private long expireMillis;

    public String genAccessToken(Member member) {
        return JwtUtil.createToken(
                secretKey,
                expireMillis,
                Map.of(
                        "id", member.getId(),
                        "email", member.getEmail()
                )
        );
    }

    public Map<String, Object> payloadOrNull(String token) {
        return JwtUtil.payloadOrNull(token, secretKey);
    }
}